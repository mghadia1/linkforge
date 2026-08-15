package com.mghadia.linkforge.link;

import com.mghadia.linkforge.cache.LinkCache;
import com.mghadia.linkforge.shortcode.Base62Codec;
import com.mghadia.linkforge.url.UrlValidator;
import java.time.Clock;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LinkService {

    private final LinkRepository links;
    private final LinkCache cache;
    private final Clock clock;

    public LinkService(LinkRepository links, LinkCache cache, Clock clock) {
        this.links = links;
        this.cache = cache;
        this.clock = clock;
    }

    @Transactional
    public Link create(String rawUrl, long ownerId) {
        String longUrl = UrlValidator.validate(rawUrl);
        String code = Base62Codec.encode(links.nextCodeValue());
        return links.save(new Link(code, longUrl, ownerId, clock.instant()));
    }

    /**
     * The redirect path: cache first, database second.
     *
     * <p>Deliberately not {@code @Transactional}. A cache hit must not open a database
     * transaction, and wrapping this method would take a connection from the pool on every
     * redirect — including the ones the cache exists to keep away from the database.
     */
    public ResolvedLink resolveForRedirect(String code) {
        return switch (cache.lookup(code)) {
            case LinkCache.Lookup.Hit hit -> hit.link();
            case LinkCache.Lookup.KnownMiss ignored -> throw new LinkNotFoundException(code);
            case LinkCache.Lookup.Unknown ignored -> readThrough(code);
        };
    }

    private ResolvedLink readThrough(String code) {
        Optional<Link> found = links.findByCode(code);
        if (found.isEmpty()) {
            cache.putMiss(code);
            throw new LinkNotFoundException(code);
        }
        ResolvedLink resolved = ResolvedLink.of(found.get());
        cache.put(code, resolved);
        return resolved;
    }

    /**
     * The full record, for metadata and stats. Uncached on purpose: those endpoints are rare, they
     * want fields the cache does not hold, and stats must never be served from a stale copy.
     *
     * <p>A link owned by someone else is reported as <b>not found</b>, not as forbidden. A 403
     * would confirm that the code exists and belongs to another account, which is exactly the fact
     * being protected — and since codes are sequential, that answer is enumerable.
     */
    @Transactional(readOnly = true)
    public Link resolveOwned(String code, long ownerId) {
        Link link = links.findByCode(code).orElseThrow(() -> new LinkNotFoundException(code));
        if (!link.isOwnedBy(ownerId)) {
            throw new LinkNotFoundException(code);
        }
        return link;
    }

    @Transactional(readOnly = true)
    public Page<Link> listOwnedBy(long ownerId, Pageable pageable) {
        return links.findByUserIdOrderByCreatedAtDesc(ownerId, pageable);
    }
}

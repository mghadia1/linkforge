package com.mghadia.linkforge.cache;

import com.mghadia.linkforge.link.ResolvedLink;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Caches {@code code -> (id, long URL)} in Redis.
 *
 * <p>Redirects vastly outnumber creates, and every redirect is the same indexed single-row read.
 * That is the whole case for this class.
 *
 * <p><b>Values are encoded by hand, not serialized.</b> A stored Java object ties the cache to the
 * class it was written from, so a rename turns every warm entry into a deserialization error at
 * the worst possible moment. {@code id:url} survives any refactor and is readable in
 * {@code redis-cli}.
 *
 * <p><b>Absence is cached too.</b> Without it, requests for codes that do not exist reach Postgres
 * every single time, so anyone hitting random codes bypasses the cache entirely and aims their
 * traffic straight at the database. Negative entries expire faster than real ones, so a code that
 * is created after someone probed for it becomes visible quickly.
 *
 * <p><b>Nothing here throws.</b> Every Redis call is wrapped: if Redis is down, the redirect must
 * still work by falling through to Postgres, slower but correct.
 */
public class RedisLinkCache implements LinkCache {

    private static final Logger log = LoggerFactory.getLogger(RedisLinkCache.class);
    private static final String KEY_PREFIX = "linkforge:code:";
    private static final String MISS_MARKER = "-";

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final Duration missTtl;
    private final Counter hits;
    private final Counter misses;
    private final Counter errors;

    public RedisLinkCache(StringRedisTemplate redis, Duration ttl, Duration missTtl,
            MeterRegistry meters) {
        this.redis = redis;
        this.ttl = ttl;
        this.missTtl = missTtl;
        this.hits = Counter.builder("linkforge.cache.hits")
                .description("lookups answered by Redis, including cached absences").register(meters);
        this.misses = Counter.builder("linkforge.cache.misses")
                .description("lookups that had to read the database").register(meters);
        this.errors = Counter.builder("linkforge.cache.errors")
                .description("cache operations that failed and fell through to the database")
                .register(meters);
    }

    @Override
    public Lookup lookup(String code) {
        String value;
        try {
            value = redis.opsForValue().get(key(code));
        } catch (RuntimeException e) {
            errors.increment();
            log.warn("cache lookup failed for {}, falling through to the database: {}", code,
                    e.toString());
            return new Lookup.Unknown();
        }
        if (value == null) {
            misses.increment();
            return new Lookup.Unknown();
        }
        hits.increment();
        if (MISS_MARKER.equals(value)) {
            return new Lookup.KnownMiss();
        }
        return decode(value).<Lookup>map(Lookup.Hit::new).orElseGet(Lookup.Unknown::new);
    }

    @Override
    public void put(String code, ResolvedLink link) {
        write(code, link.id() + ":" + link.longUrl(), ttl);
    }

    @Override
    public void putMiss(String code) {
        write(code, MISS_MARKER, missTtl);
    }

    private void write(String code, String value, Duration expiry) {
        try {
            redis.opsForValue().set(key(code), value, expiry);
        } catch (RuntimeException e) {
            // A failed write costs a future cache hit and nothing else.
            errors.increment();
            log.warn("cache write failed for {}: {}", code, e.toString());
        }
    }

    private Optional<ResolvedLink> decode(String value) {
        int separator = value.indexOf(':');
        if (separator <= 0) {
            return Optional.empty();
        }
        try {
            long id = Long.parseLong(value.substring(0, separator));
            // Only the first colon separates; the URL keeps every colon it contains.
            return Optional.of(new ResolvedLink(id, value.substring(separator + 1)));
        } catch (NumberFormatException e) {
            // Garbage or an entry written by an older encoding: ignore it and re-read the row.
            log.warn("ignoring malformed cache entry: {}", value);
            return Optional.empty();
        }
    }

    private String key(String code) {
        return KEY_PREFIX + code;
    }
}

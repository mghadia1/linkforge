package com.mghadia.linkforge.link;

import com.mghadia.linkforge.auth.CurrentUser;
import com.mghadia.linkforge.click.ClickStatsService;
import com.mghadia.linkforge.click.dto.StatsResponse;
import com.mghadia.linkforge.link.dto.CreateLinkRequest;
import com.mghadia.linkforge.link.dto.LinkResponse;
import com.mghadia.linkforge.web.LinkForgeProperties;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The management API lives under {@code /api} so that the whole of the root path stays available
 * for short codes. If creation were at {@code /links}, a generated code of "links" would shadow
 * it — a bug that only appears once the sequence happens to reach that value.
 *
 * <p>Every endpoint here requires a token and operates only on the caller's own links. The owner
 * comes from the validated token, never from the request.
 */
@RestController
@RequestMapping("/api/links")
public class LinkController {

    private static final int MAX_PAGE_SIZE = 100;

    private final LinkService links;
    private final ClickStatsService stats;
    private final LinkForgeProperties properties;

    public LinkController(LinkService links, ClickStatsService stats,
            LinkForgeProperties properties) {
        this.links = links;
        this.stats = stats;
        this.properties = properties;
    }

    @PostMapping
    public ResponseEntity<LinkResponse> create(@AuthenticationPrincipal Jwt token,
            @Valid @RequestBody CreateLinkRequest request) {
        Link link = links.create(request.url(), CurrentUser.idOf(token));
        LinkResponse body = LinkResponse.of(link, properties.baseUrl());
        return ResponseEntity.created(URI.create(body.shortUrl())).body(body);
    }

    @GetMapping
    public PagedLinks list(@AuthenticationPrincipal Jwt token,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Link> found = links.listOwnedBy(CurrentUser.idOf(token),
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE)));
        return new PagedLinks(
                found.getContent().stream()
                        .map(link -> LinkResponse.of(link, properties.baseUrl())).toList(),
                found.getNumber(), found.getSize(), found.getTotalElements(),
                found.getTotalPages());
    }

    @GetMapping("/{code}")
    public LinkResponse get(@AuthenticationPrincipal Jwt token, @PathVariable String code) {
        return LinkResponse.of(links.resolveOwned(code, CurrentUser.idOf(token)),
                properties.baseUrl());
    }

    @GetMapping("/{code}/stats")
    public StatsResponse stats(@AuthenticationPrincipal Jwt token, @PathVariable String code,
            @RequestParam(defaultValue = "30") int days) {
        return stats.statsFor(code, CurrentUser.idOf(token), days);
    }

    public record PagedLinks(List<LinkResponse> links, int page, int size, long totalElements,
            int totalPages) {
    }
}

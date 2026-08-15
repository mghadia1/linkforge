package com.mghadia.linkforge.link;

import com.mghadia.linkforge.click.ClickEvent;
import com.mghadia.linkforge.click.ClickRecorder;
import com.mghadia.linkforge.click.RequestMetadata;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The hot path: every visit to a short link lands here.
 *
 * <p>Nothing on this path blocks on anything it does not have to: the lookup is served from Redis
 * when it can be, and the click is handed to a background writer rather than inserted inline.
 *
 * <p>The status is 302, not the 301 a shortener's diagram usually shows. A 301 is cached by the
 * browser indefinitely, so the second visit never reaches this service — which is fast, but it
 * makes click analytics undercount and makes a link's target impossible to change. 302 keeps
 * every visit observable, and that is the whole point of the analytics half of this project.
 */
@RestController
public class RedirectController {

    private final LinkService links;
    private final ClickRecorder clicks;
    private final Clock clock;

    public RedirectController(LinkService links, ClickRecorder clicks, Clock clock) {
        this.links = links;
        this.clicks = clicks;
        this.clock = clock;
    }

    // The pattern keeps this handler from swallowing every unmatched path (/favicon.ico,
    // /actuator/health) — those must still 404 or route normally.
    @GetMapping("/{code:[0-9A-Za-z]{1,16}}")
    public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
        ResolvedLink link = links.resolveForRedirect(code);

        // Read the request's headers here, on the request thread, and hand the worker a value
        // object. The recorder returns immediately and never throws, so nothing about click
        // logging can delay or break the redirect.
        clicks.record(new ClickEvent(link.id(), clock.instant(),
                RequestMetadata.referrerHost(request), RequestMetadata.anonymizedIp(request)));

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(link.longUrl()));
        headers.setCacheControl("no-store");
        return new ResponseEntity<>(headers, HttpStatus.FOUND);
    }
}

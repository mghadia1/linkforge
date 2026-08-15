package com.mghadia.linkforge.click;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.mghadia.linkforge.AbstractIntegrationTest;
import com.mghadia.linkforge.click.dto.StatsResponse;
import com.mghadia.linkforge.link.dto.CreateLinkRequest;
import com.mghadia.linkforge.link.dto.LinkResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class ClickStatsIntegrationTest extends AbstractIntegrationTest {

    /** Clicks are written off the request thread, so every assertion on them has to wait. */
    private static final Duration WRITE_TIMEOUT = Duration.ofSeconds(5);

    @Test
    void redirectsAreCountedWithTheirReferrers() {
        String code = shorten("https://example.com/counted");

        visit(code, "https://news.example.com/article", "203.0.113.5");
        visit(code, "https://news.example.com/other", "203.0.113.9");
        visit(code, "https://www.aggregator.example/feed", "198.51.100.4");
        visit(code, null, "198.51.100.4");

        StatsResponse stats = awaitTotalClicks(code, 4);

        assertThat(stats.code()).isEqualTo(code);
        assertThat(stats.longUrl()).isEqualTo("https://example.com/counted");
        assertThat(stats.firstClickAt()).isNotNull();
        assertThat(stats.lastClickAt()).isNotNull();
        assertThat(stats.topReferrers()).containsExactly(
                new StatsResponse.ReferrerClicks("news.example.com", 2),
                new StatsResponse.ReferrerClicks("aggregator.example", 1),
                new StatsResponse.ReferrerClicks("direct", 1));
    }

    @Test
    void uniqueVisitorsCountDistinctAnonymizedAddresses() {
        String code = shorten("https://example.com/visitors");

        // Same /24, different hosts: anonymization collapses these to one visitor.
        visit(code, null, "203.0.113.11");
        visit(code, null, "203.0.113.12");
        visit(code, null, "198.51.100.7");

        StatsResponse stats = awaitTotalClicks(code, 3);

        assertThat(stats.uniqueVisitors()).isEqualTo(2);
    }

    @Test
    void clicksByDayIsZeroFilledAcrossTheWindow() {
        String code = shorten("https://example.com/series");
        visit(code, null, "192.0.2.30");

        StatsResponse stats = awaitTotalClicks(code, 1, 7);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        assertThat(stats.windowDays()).isEqualTo(7);
        assertThat(stats.clicksByDay()).hasSize(7);
        assertThat(stats.clicksByDay().getFirst().day()).isEqualTo(today.minusDays(6));
        assertThat(stats.clicksByDay().getLast())
                .isEqualTo(new StatsResponse.DailyClicks(today, 1));
        assertThat(stats.clicksByDay().subList(0, 6)).allMatch(day -> day.clicks() == 0);
    }

    @Test
    void aLinkWithNoClicksReportsZerosRatherThanFailing() {
        String code = shorten("https://example.com/untouched");

        StatsResponse stats = rest.getForObject("/api/links/" + code + "/stats?days=30",
                StatsResponse.class);

        assertThat(stats.totalClicks()).isZero();
        assertThat(stats.uniqueVisitors()).isZero();
        assertThat(stats.firstClickAt()).isNull();
        assertThat(stats.topReferrers()).isEmpty();
        assertThat(stats.clicksByDay()).hasSize(30).allMatch(day -> day.clicks() == 0);
    }

    @Test
    void anAbsurdWindowIsClampedRatherThanRejected() {
        String code = shorten("https://example.com/clamped");

        assertThat(statsFor(code, 100_000).windowDays()).isEqualTo(365);
        assertThat(statsFor(code, 0).windowDays()).isEqualTo(1);
    }

    @Test
    void statsForAnUnknownCodeIs404() {
        ResponseEntity<Map> response = rest.getForEntity("/api/links/nosuch/stats", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theRedirectIsServedWhetherOrNotTheClickHasBeenWritten() {
        String code = shorten("https://example.com/fast-path");

        ResponseEntity<Void> redirect = visit(code, null, "192.0.2.99");

        // Asserted immediately, before any wait: the response does not depend on the write.
        assertThat(redirect.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirect.getHeaders().getLocation())
                .hasToString("https://example.com/fast-path");
    }

    private String shorten(String url) {
        return rest.postForObject("/api/links", new CreateLinkRequest(url), LinkResponse.class)
                .code();
    }

    private ResponseEntity<Void> visit(String code, String referer, String forwardedFor) {
        HttpHeaders headers = new HttpHeaders();
        if (referer != null) {
            headers.add("Referer", referer);
        }
        headers.add("X-Forwarded-For", forwardedFor);
        return rest.exchange("/" + code, HttpMethod.GET, new HttpEntity<>(headers), Void.class);
    }

    private StatsResponse statsFor(String code, int days) {
        return rest.getForObject("/api/links/" + code + "/stats?days=" + days, StatsResponse.class);
    }

    private StatsResponse awaitTotalClicks(String code, long expected) {
        return awaitTotalClicks(code, expected, 30);
    }

    private StatsResponse awaitTotalClicks(String code, long expected, int days) {
        await().atMost(WRITE_TIMEOUT)
                .until(() -> statsFor(code, days).totalClicks() == expected);
        return statsFor(code, days);
    }
}

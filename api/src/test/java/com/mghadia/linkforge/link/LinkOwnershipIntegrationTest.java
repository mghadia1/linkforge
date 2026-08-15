package com.mghadia.linkforge.link;

import static org.assertj.core.api.Assertions.assertThat;

import com.mghadia.linkforge.AbstractIntegrationTest;
import com.mghadia.linkforge.link.dto.CreateLinkRequest;
import com.mghadia.linkforge.link.dto.LinkResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * One user must not be able to see, or learn anything about, another user's links. Since codes
 * are sequential, guessing a neighbouring code is trivial, so these are the tests that decide
 * whether the analytics are actually private.
 */
class LinkOwnershipIntegrationTest extends AbstractIntegrationTest {

    private TestRestTemplate intruder;

    @BeforeEach
    void registerASecondUser() {
        intruder = client(register("intruder-" + UUID.randomUUID() + "@example.com",
                "correct horse battery"));
    }

    @Test
    void anotherUsersLinkIsReportedAsNotFoundRatherThanForbidden() {
        String code = shorten("https://example.com/private");

        ResponseEntity<Map> response = intruder.getForEntity("/api/links/" + code, Map.class);

        // 403 would confirm the code exists and belongs to someone; 404 says nothing at all.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void anotherUsersStatsAreNotReadable() {
        String code = shorten("https://example.com/private-stats");
        rest.getForEntity("/" + code, Void.class);

        ResponseEntity<Map> response =
                intruder.getForEntity("/api/links/" + code + "/stats", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theListOnlyContainsTheCallersOwnLinks() {
        String mine = shorten("https://example.com/mine");
        String theirs = intruder.postForObject("/api/links",
                new CreateLinkRequest("https://example.com/theirs"), LinkResponse.class).code();

        LinkController.PagedLinks page =
                rest.getForObject("/api/links", LinkController.PagedLinks.class);

        assertThat(page.links()).extracting(LinkResponse::code).contains(mine)
                .doesNotContain(theirs);
        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    void theListIsNewestFirstAndPaginates() {
        List.of("https://example.com/one", "https://example.com/two", "https://example.com/three")
                .forEach(this::shorten);

        LinkController.PagedLinks firstPage =
                rest.getForObject("/api/links?page=0&size=2", LinkController.PagedLinks.class);

        assertThat(firstPage.links()).hasSize(2);
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.links().getFirst().longUrl()).isEqualTo("https://example.com/three");

        LinkController.PagedLinks secondPage =
                rest.getForObject("/api/links?page=1&size=2", LinkController.PagedLinks.class);
        assertThat(secondPage.links()).hasSize(1);
        assertThat(secondPage.links().getFirst().longUrl()).isEqualTo("https://example.com/one");
    }

    @Test
    void anAbsurdPageSizeIsClamped() {
        shorten("https://example.com/clamped");

        LinkController.PagedLinks page =
                rest.getForObject("/api/links?size=100000", LinkController.PagedLinks.class);

        assertThat(page.size()).isEqualTo(100);
    }

    /** Ownership must not block the public redirect — anyone may follow anyone's link. */
    @Test
    void anotherUsersLinkStillRedirects() {
        String code = shorten("https://example.com/still-public");

        ResponseEntity<Void> redirect = intruder.getForEntity("/" + code, Void.class);

        assertThat(redirect.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirect.getHeaders().getLocation())
                .hasToString("https://example.com/still-public");
    }

    private String shorten(String url) {
        return rest.postForObject("/api/links", new CreateLinkRequest(url), LinkResponse.class)
                .code();
    }
}

package com.mghadia.linkforge.link;

import static org.assertj.core.api.Assertions.assertThat;

import com.mghadia.linkforge.AbstractIntegrationTest;
import com.mghadia.linkforge.link.dto.CreateLinkRequest;
import com.mghadia.linkforge.link.dto.LinkResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class LinkApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    void shortenThenRedirect() {
        String longUrl = "https://example.com/a/very/long/path?with=query";

        ResponseEntity<LinkResponse> created =
                rest.postForEntity("/api/links", new CreateLinkRequest(longUrl), LinkResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        LinkResponse body = created.getBody();
        assertThat(body).isNotNull();
        assertThat(body.longUrl()).isEqualTo(longUrl);
        assertThat(body.code()).isNotBlank().matches("[0-9A-Za-z]{1,16}");
        assertThat(body.shortUrl()).endsWith("/" + body.code());
        assertThat(body.createdAt()).isNotNull();

        ResponseEntity<Void> redirect = rest.getForEntity("/" + body.code(), Void.class);

        assertThat(redirect.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirect.getHeaders().getLocation()).hasToString(longUrl);
        // A cached redirect would hide later visits from the click analytics in build step 2.
        assertThat(redirect.getHeaders().getCacheControl()).isEqualTo("no-store");
    }

    @Test
    void fetchingAnUnknownCodeIs404() {
        ResponseEntity<Map> response = rest.getForEntity("/zzzzzzz", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void metadataEndpointReturnsTheStoredLink() {
        LinkResponse created = rest.postForObject("/api/links",
                new CreateLinkRequest("https://example.com/metadata"), LinkResponse.class);

        ResponseEntity<LinkResponse> fetched =
                rest.getForEntity("/api/links/" + created.code(), LinkResponse.class);

        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().longUrl()).isEqualTo("https://example.com/metadata");
    }

    @Test
    void rejectsADisallowedScheme() {
        ResponseEntity<Map> response = rest.postForEntity("/api/links",
                new CreateLinkRequest("javascript:alert(1)"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).extracting("message").asString().contains("scheme");
    }

    @Test
    void rejectsAMissingUrlField() {
        ResponseEntity<Map> response =
                rest.postForEntity("/api/links", new CreateLinkRequest(null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void concurrentCreatesGetDistinctCodes() throws Exception {
        int requests = 50;
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Callable<String>> tasks = IntStream.range(0, requests)
                    .<Callable<String>>mapToObj(i -> () -> rest.postForObject("/api/links",
                            new CreateLinkRequest("https://example.com/concurrent/" + i),
                            LinkResponse.class).code())
                    .toList();

            Set<String> codes = new HashSet<>();
            for (Future<String> future : pool.invokeAll(tasks)) {
                codes.add(future.get());
            }

            // The sequence, not a retry loop, is what makes this hold.
            assertThat(codes).hasSize(requests);
        }
    }
}

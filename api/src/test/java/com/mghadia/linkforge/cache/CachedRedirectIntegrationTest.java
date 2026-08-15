package com.mghadia.linkforge.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.mghadia.linkforge.AbstractIntegrationTest;
import com.mghadia.linkforge.link.dto.CreateLinkRequest;
import com.mghadia.linkforge.link.dto.LinkResponse;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The cache against a real Redis, through real HTTP. */
class CachedRedirectIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void theFirstRedirectWarmsTheCacheAndTheSecondIsServedFromIt() {
        String code = shorten("https://example.com/cached");

        assertThat(redis.opsForValue().get(key(code))).isNull();

        assertThat(redirect(code)).isEqualTo(HttpStatus.FOUND);
        assertThat(redis.opsForValue().get(key(code))).endsWith(":https://example.com/cached");

        assertThat(redirect(code)).isEqualTo(HttpStatus.FOUND);
    }

    @Test
    void aRedirectIsStillCorrectWhenServedFromTheCache() {
        String code = shorten("https://example.com/from-cache");
        redirect(code);

        ResponseEntity<Void> second = rest.getForEntity("/" + code, Void.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(second.getHeaders().getLocation())
                .hasToString("https://example.com/from-cache");
    }

    /**
     * Cache penetration: without a cached absence, every request for a code that does not exist
     * reaches Postgres, so random codes bypass the cache entirely.
     */
    @Test
    void anUnknownCodeIsRememberedAsAbsent() {
        String unknown = "Zq7Xk2";

        assertThat(redirect(unknown)).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(redis.opsForValue().get(key(unknown))).isEqualTo("-");
        // Still a 404, now without reading the database.
        assertThat(redirect(unknown)).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aCachedEntryDoesNotLeakIntoTheMetadataEndpoint() {
        String code = shorten("https://example.com/metadata-vs-cache");
        redirect(code);

        ResponseEntity<LinkResponse> metadata =
                rest.getForEntity("/api/links/" + code, LinkResponse.class);

        assertThat(metadata.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(metadata.getBody().longUrl()).isEqualTo("https://example.com/metadata-vs-cache");
        assertThat(metadata.getBody().createdAt()).isNotNull();
    }

    @Test
    void aCorruptCacheEntryFallsBackToTheDatabase() {
        String code = shorten("https://example.com/corrupt-entry");
        redis.opsForValue().set(key(code), "this is not a valid entry");

        ResponseEntity<Void> response = rest.getForEntity("/" + code, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation())
                .hasToString("https://example.com/corrupt-entry");
    }

    private String key(String code) {
        return "linkforge:code:" + code;
    }

    private String shorten(String url) {
        return rest.postForObject("/api/links", new CreateLinkRequest(url), LinkResponse.class)
                .code();
    }

    private HttpStatus redirect(String code) {
        return (HttpStatus) rest.getForEntity("/" + code, Map.class).getStatusCode();
    }
}

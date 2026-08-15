package com.mghadia.linkforge.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.mghadia.linkforge.AbstractIntegrationTest;
import com.mghadia.linkforge.link.dto.CreateLinkRequest;
import com.mghadia.linkforge.link.dto.LinkResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

/**
 * The configuration the "before" half of the latency benchmark runs under. It has to be tested,
 * not assumed: a benchmark whose control arm is quietly broken produces a flattering number.
 */
@TestPropertySource(properties = "linkforge.cache.enabled=false")
class CacheDisabledIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LinkCache cache;

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void theNoOpCacheIsWiredIn() {
        assertThat(cache).isInstanceOf(NoOpLinkCache.class);
    }

    @Test
    void redirectsStillWorkAndNothingIsWrittenToRedis() {
        String code = rest.postForObject("/api/links",
                new CreateLinkRequest("https://example.com/uncached"), LinkResponse.class).code();

        ResponseEntity<Void> redirect = rest.getForEntity("/" + code, Void.class);

        assertThat(redirect.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirect.getHeaders().getLocation()).hasToString("https://example.com/uncached");
        assertThat(redis.opsForValue().get("linkforge:code:" + code)).isNull();
    }
}

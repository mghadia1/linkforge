package com.mghadia.linkforge.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mghadia.linkforge.link.ResolvedLink;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * Behaviour that must hold without a Redis to talk to — above all, what happens when Redis is
 * unreachable.
 */
class RedisLinkCacheTest {

    private static final String KEY = "linkforge:code:4C92";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private MeterRegistry meters;
    private RedisLinkCache cache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        meters = new SimpleMeterRegistry();
        cache = new RedisLinkCache(redis, Duration.ofHours(1), Duration.ofSeconds(30), meters);
    }

    @Test
    void aStoredEntryIsAHit() {
        when(values.get(KEY)).thenReturn("42:https://example.com/path");

        assertThat(cache.lookup("4C92"))
                .isEqualTo(new LinkCache.Lookup.Hit(new ResolvedLink(42, "https://example.com/path")));
        assertThat(counter("linkforge.cache.hits")).isEqualTo(1.0);
    }

    @Test
    void theUrlKeepsEveryColonAfterTheFirst() {
        when(values.get(KEY)).thenReturn("7:https://example.com:8443/a:b?c=d:e");

        assertThat(cache.lookup("4C92")).isEqualTo(new LinkCache.Lookup.Hit(
                new ResolvedLink(7, "https://example.com:8443/a:b?c=d:e")));
    }

    @Test
    void anAbsentKeyIsUnknown() {
        when(values.get(KEY)).thenReturn(null);

        assertThat(cache.lookup("4C92")).isInstanceOf(LinkCache.Lookup.Unknown.class);
        assertThat(counter("linkforge.cache.misses")).isEqualTo(1.0);
    }

    @Test
    void aStoredMarkerIsAKnownMiss() {
        when(values.get(KEY)).thenReturn("-");

        assertThat(cache.lookup("4C92")).isInstanceOf(LinkCache.Lookup.KnownMiss.class);
    }

    /**
     * The failure mode that decides whether this cache is safe to deploy: Redis being down must
     * cost latency, not availability. Every lookup degrades to Unknown so the caller reads
     * Postgres.
     */
    @Test
    void anUnreachableRedisDegradesToUnknownInsteadOfThrowing() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("no route"));

        assertThat(cache.lookup("4C92")).isInstanceOf(LinkCache.Lookup.Unknown.class);
        assertThat(counter("linkforge.cache.errors")).isEqualTo(1.0);
    }

    @Test
    void aFailedWriteIsSwallowed() {
        doThrow(new RedisConnectionFailureException("no route"))
                .when(values).set(anyString(), anyString(), any(Duration.class));

        assertThatCode(() -> cache.put("4C92", new ResolvedLink(1, "https://example.com")))
                .doesNotThrowAnyException();
        assertThatCode(() -> cache.putMiss("4C92")).doesNotThrowAnyException();
        assertThat(counter("linkforge.cache.errors")).isEqualTo(2.0);
    }

    @Test
    void aMalformedEntryIsIgnoredRatherThanServed() {
        when(values.get(KEY)).thenReturn("not-an-id:https://example.com");

        // Falls back to the database instead of redirecting somewhere unverified.
        assertThat(cache.lookup("4C92")).isInstanceOf(LinkCache.Lookup.Unknown.class);
    }

    private double counter(String name) {
        return meters.get(name).counter().count();
    }
}

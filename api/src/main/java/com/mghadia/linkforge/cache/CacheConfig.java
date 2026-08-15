package com.mghadia.linkforge.cache;

import com.mghadia.linkforge.web.LinkForgeProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class CacheConfig {

    @Bean
    @ConditionalOnProperty(name = "linkforge.cache.enabled", havingValue = "true",
            matchIfMissing = true)
    public LinkCache redisLinkCache(StringRedisTemplate redis, LinkForgeProperties properties,
            MeterRegistry meters) {
        LinkForgeProperties.Cache cache = properties.cache();
        return new RedisLinkCache(redis, cache.ttl(), cache.missTtl(), meters);
    }

    @Bean
    @ConditionalOnProperty(name = "linkforge.cache.enabled", havingValue = "false")
    public LinkCache noOpLinkCache() {
        return new NoOpLinkCache();
    }
}

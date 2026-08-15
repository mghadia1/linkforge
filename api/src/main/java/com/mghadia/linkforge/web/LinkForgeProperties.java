package com.mghadia.linkforge.web;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl origin the short URL is built from, e.g. {@code http://localhost:8080}
 * @param cache   redirect cache settings
 */
@ConfigurationProperties(prefix = "linkforge")
public record LinkForgeProperties(String baseUrl, Cache cache) {

    public LinkForgeProperties {
        cache = cache == null ? Cache.defaults() : cache;
    }

    /**
     * @param enabled false swaps in a no-op cache, which is how the before/after latency numbers
     *                are measured against one unchanged code path
     * @param ttl     how long a resolved link stays cached
     * @param missTtl how long a known-absent code stays cached; deliberately shorter, so a code
     *                created after someone probed for it does not stay invisible
     */
    public record Cache(boolean enabled, Duration ttl, Duration missTtl) {

        static Cache defaults() {
            return new Cache(true, Duration.ofHours(1), Duration.ofSeconds(30));
        }
    }
}

package com.mghadia.linkforge.web;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LinkForgeProperties.class)
public class AppConfig {

    /** Injected rather than called statically so tests can pin time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

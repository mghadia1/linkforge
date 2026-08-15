package com.mghadia.linkforge;

import java.util.Map;
import java.util.UUID;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Integration tests run against a real Postgres in Docker rather than an in-memory database, so
 * the Flyway migrations, the sequence, and the unique index are all exercised as they are in
 * production. Requires a running Docker daemon.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    /**
     * One container for the whole JVM, started here rather than by {@code @Testcontainers}.
     *
     * <p>The JUnit extension ties a {@code @Container} field to the lifecycle of a single test
     * class, but Spring caches and reuses the application context across classes. The two
     * lifetimes do not match: the first class to finish stops the database while the second class
     * inherits a context whose connection pool still points at it, and every later test fails with
     * a connection timeout rather than anything resembling its actual assertion. Starting the
     * container once and letting Testcontainers' Ryuk sidecar reap it at JVM exit keeps the
     * database alive for as long as the context that uses it.
     */
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @LocalServerPort
    protected int port;

    /** Authenticated as a fresh user, unique per test. Most tests want this one. */
    protected TestRestTemplate rest;

    /** No Authorization header. For asserting that endpoints actually require a token. */
    protected TestRestTemplate anonymous;

    /** The token {@link #rest} carries, for tests that need to build their own requests. */
    protected String token;

    /**
     * The HTTP client must not follow redirects: these tests assert on the 302 and its Location
     * header, and a following client would instead chase the link out to the real internet and
     * report whatever that host answered.
     */
    @BeforeEach
    void setUpClients() {
        anonymous = client(null);
        // A new account per test, so no test can see another's links and ordering cannot matter.
        token = register("user-" + UUID.randomUUID() + "@example.com", "correct horse battery");
        rest = client(token);
    }

    protected String register(String email, String password) {
        Map<String, String> body = Map.of("email", email, "password", password);
        Map<?, ?> response = anonymous.postForObject("/api/auth/register", body, Map.class);
        return (String) response.get("token");
    }

    protected TestRestTemplate client(String bearerToken) {
        CloseableHttpClient httpClient = HttpClients.custom().disableRedirectHandling().build();
        RestTemplateBuilder builder = new RestTemplateBuilder()
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(httpClient))
                .rootUri("http://localhost:" + port);
        if (bearerToken != null) {
            builder = builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }
        return new TestRestTemplate(builder);
    }
}

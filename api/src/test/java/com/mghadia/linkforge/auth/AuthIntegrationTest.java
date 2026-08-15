package com.mghadia.linkforge.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.mghadia.linkforge.AbstractIntegrationTest;
import com.mghadia.linkforge.link.dto.CreateLinkRequest;
import com.mghadia.linkforge.link.dto.LinkResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void registerReturnsAUsableToken() {
        Map<String, String> body = credentials();

        ResponseEntity<Map> response =
                anonymous.postForEntity("/api/auth/register", body, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKeys("token", "tokenType", "expiresAt");
        assertThat(response.getBody().get("tokenType")).isEqualTo("Bearer");

        TestClient authenticated = new TestClient((String) response.getBody().get("token"));
        assertThat(authenticated.createLink("https://example.com/after-register"))
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void registeringAnAddressTwiceIsRejected() {
        Map<String, String> body = credentials();
        anonymous.postForEntity("/api/auth/register", body, Map.class);

        ResponseEntity<Map> second =
                anonymous.postForEntity("/api/auth/register", body, Map.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void addressesAreComparedWithoutCaseSensitivity() {
        String email = "Mixed-" + UUID.randomUUID() + "@Example.COM";
        anonymous.postForEntity("/api/auth/register",
                Map.of("email", email, "password", "correct horse battery"), Map.class);

        ResponseEntity<Map> duplicate = anonymous.postForEntity("/api/auth/register",
                Map.of("email", email.toLowerCase(), "password", "correct horse battery"),
                Map.class);

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void loginWithTheRightPasswordReturnsAToken() {
        Map<String, String> body = credentials();
        anonymous.postForEntity("/api/auth/register", body, Map.class);

        ResponseEntity<Map> response = anonymous.postForEntity("/api/auth/login", body, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("token")).asString().isNotBlank();
    }

    @Test
    void loginCanUseADifferentlyCasedAddress() {
        String email = "Case-" + UUID.randomUUID() + "@Example.com";
        anonymous.postForEntity("/api/auth/register",
                Map.of("email", email, "password", "correct horse battery"), Map.class);

        ResponseEntity<Map> response = anonymous.postForEntity("/api/auth/login",
                Map.of("email", email.toUpperCase(), "password", "correct horse battery"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * A wrong password and an address with no account must be indistinguishable, or the login
     * endpoint tells an attacker which addresses are worth attacking.
     */
    @Test
    void aWrongPasswordAndAnUnknownAddressGiveIdenticalAnswers() {
        Map<String, String> body = credentials();
        anonymous.postForEntity("/api/auth/register", body, Map.class);

        ResponseEntity<Map> wrongPassword = anonymous.postForEntity("/api/auth/login",
                Map.of("email", body.get("email"), "password", "not the password"), Map.class);
        ResponseEntity<Map> unknownAddress = anonymous.postForEntity("/api/auth/login",
                Map.of("email", "nobody-" + UUID.randomUUID() + "@example.com",
                        "password", "not the password"), Map.class);

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownAddress.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getBody().get("message"))
                .isEqualTo(unknownAddress.getBody().get("message"));
    }

    @Test
    void registrationValidatesTheAddressAndPasswordLength() {
        assertThat(anonymous.postForEntity("/api/auth/register",
                Map.of("email", "not-an-address", "password", "correct horse battery"), Map.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(anonymous.postForEntity("/api/auth/register",
                Map.of("email", "short-" + UUID.randomUUID() + "@example.com", "password", "abc"),
                Map.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void protectedEndpointsRejectAMissingToken() {
        assertThat(new TestClient(null).createLink("https://example.com/no-token"))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.getForEntity("/api/links", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedEndpointsRejectAGarbageOrTamperedToken() {
        assertThat(new TestClient("not.a.jwt").createLink("https://example.com/garbage"))
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        // Flip the last character of the signature: same header and payload, wrong signature.
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");
        assertThat(new TestClient(tampered).createLink("https://example.com/tampered"))
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** Actuator metrics were reachable without a token before this step. */
    @Test
    void actuatorMetricsNowRequireAToken() {
        assertThat(anonymous.getForEntity("/actuator/metrics", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.getForEntity("/actuator/metrics", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void healthStaysOpenForProbes() {
        assertThat(anonymous.getForEntity("/actuator/health", Map.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    /** The redirect is the public product. Requiring a token here would break every short link. */
    @Test
    void theRedirectRemainsPublic() {
        String code = rest.postForObject("/api/links",
                new CreateLinkRequest("https://example.com/public-redirect"), LinkResponse.class)
                .code();

        ResponseEntity<Void> redirect = anonymous.getForEntity("/" + code, Void.class);

        assertThat(redirect.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(redirect.getHeaders().getLocation())
                .hasToString("https://example.com/public-redirect");
    }

    private Map<String, String> credentials() {
        return Map.of("email", "auth-" + UUID.randomUUID() + "@example.com",
                "password", "correct horse battery");
    }

    /** Small wrapper so each assertion reads as one line. */
    private class TestClient {

        private final org.springframework.boot.test.web.client.TestRestTemplate template;

        TestClient(String bearerToken) {
            this.template = client(bearerToken);
        }

        HttpStatus createLink(String url) {
            return (HttpStatus) template
                    .postForEntity("/api/links", new CreateLinkRequest(url), Map.class)
                    .getStatusCode();
        }
    }
}

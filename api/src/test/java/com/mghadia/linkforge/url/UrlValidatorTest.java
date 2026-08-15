package com.mghadia.linkforge.url;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UrlValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "https://example.com",
        "http://example.com/path?query=1#fragment",
        "HTTPS://Example.com/Mixed-Case",
        "https://sub.domain.example.co.uk/a/b/c"
    })
    void acceptsHttpAndHttpsUrls(String url) {
        assertThat(UrlValidator.validate(url)).isEqualTo(url);
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(UrlValidator.validate("  https://example.com  ")).isEqualTo("https://example.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsBlankInput(String url) {
        assertThatThrownBy(() -> UrlValidator.validate(url)).isInstanceOf(InvalidUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "javascript:alert(1)",
        "file:///etc/passwd",
        "data:text/html;base64,PHNjcmlwdD4=",
        "ftp://example.com/file"
    })
    void rejectsSchemesOutsideTheAllowlist(String url) {
        assertThatThrownBy(() -> UrlValidator.validate(url))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("scheme");
    }

    @Test
    void rejectsRelativeInput() {
        assertThatThrownBy(() -> UrlValidator.validate("example.com/path"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsUrlWithoutHost() {
        assertThatThrownBy(() -> UrlValidator.validate("http:///path"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("host");
    }

    @Test
    void rejectsEmbeddedUserInfo() {
        assertThatThrownBy(() -> UrlValidator.validate("https://bank.com@evil.example/login"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("user info");
    }

    @Test
    void rejectsOverlongUrls() {
        String url = "https://example.com/" + "a".repeat(2100);
        assertThatThrownBy(() -> UrlValidator.validate(url))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("2048");
    }
}

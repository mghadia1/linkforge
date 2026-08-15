package com.mghadia.linkforge.click;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestMetadataTest {

    @ParameterizedTest
    @CsvSource({
        "https://news.example.com/article, news.example.com",
        "https://WWW.Example.COM/page, example.com",
        "http://example.com:8080/a?b=c#d, example.com"
    })
    void extractsTheReferrerHost(String referer, String expected) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Referer", referer);

        assertThat(RequestMetadata.referrerHost(request)).isEqualTo(expected);
    }

    @Test
    void aDirectVisitHasNoReferrerHost() {
        assertThat(RequestMetadata.referrerHost(new MockHttpServletRequest())).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a url", "   ", "///", "mailto:someone@example.com"})
    void anUnusableRefererIsTreatedAsDirect(String referer) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Referer", referer);

        assertThat(RequestMetadata.referrerHost(request)).isNull();
    }

    @Test
    void prefersTheFirstForwardedForEntryOverTheSocketAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", "203.0.113.42, 70.41.3.18, 150.172.238.178");

        assertThat(RequestMetadata.anonymizedIp(request)).isEqualTo("203.0.113.0");
    }

    @Test
    void fallsBackToTheSocketAddressWithoutTheHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.77");

        assertThat(RequestMetadata.anonymizedIp(request)).isEqualTo("198.51.100.0");
    }

    @Test
    void zeroesTheLastOctetOfIpv4() {
        assertThat(RequestMetadata.anonymize("192.0.2.201")).isEqualTo("192.0.2.0");
    }

    @Test
    void keepsOnlyTheRoutingPrefixOfIpv6() {
        // Everything below the /48 is discarded, so the interface identifier never lands in the
        // database.
        assertThat(RequestMetadata.anonymize("2001:db8:1234:5678:9abc:def0:1234:5678"))
                .isEqualTo("2001:db8:1234:0:0:0:0:0");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "not-an-ip", "1.2.3", "1.2.3.4.5", "999.1.1.1", "1.2.3.abc"})
    void anUnparseableAddressBecomesNull(String address) {
        assertThat(RequestMetadata.anonymize(address)).isNull();
    }
}

package com.mghadia.linkforge.click;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;

/**
 * Pulls the analytics fields out of a request. Every method here runs on the request thread.
 */
public final class RequestMetadata {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String REFERER = "Referer"; // misspelled in RFC 1945 and never fixed

    private RequestMetadata() {
    }

    /**
     * The referring site, host only and lowercased, with a leading {@code www.} removed so that
     * {@code www.example.com} and {@code example.com} are one row in the dashboard.
     *
     * @return the host, or {@code null} for a direct visit or an unparseable referrer
     */
    public static String referrerHost(HttpServletRequest request) {
        String referer = request.getHeader(REFERER);
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            String host = new URI(referer.trim()).getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            // A malformed Referer is a client's problem, never the redirect's.
            return null;
        }
    }

    /**
     * The client address with its host identifier removed: the last octet of an IPv4 address, or
     * everything below the /48 of an IPv6 address.
     *
     * <p>Note the trust boundary: {@code X-Forwarded-For} is set by the client and is only
     * meaningful when the app sits behind a proxy that overwrites it. Behind AWS ALB or CloudFront
     * that holds; exposed directly it does not, and a client could forge this value. Restricting
     * the header to known proxy addresses belongs to the deployment step.
     *
     * @return the anonymized address, or {@code null} if no usable address was present
     */
    public static String anonymizedIp(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR);
        String candidate = null;
        if (forwarded != null && !forwarded.isBlank()) {
            // The client's address is the first entry; each proxy appends its own.
            candidate = forwarded.split(",")[0].trim();
        }
        if (candidate == null || candidate.isBlank()) {
            candidate = request.getRemoteAddr();
        }
        return anonymize(candidate);
    }

    static String anonymize(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String trimmed = address.trim();
        // Everything here is parsed as a literal. InetAddress.getByName would resolve a hostname
        // over DNS, which must never happen on a request thread.
        return trimmed.indexOf(':') >= 0 ? anonymizeIpv6(trimmed) : anonymizeIpv4(trimmed);
    }

    private static String anonymizeIpv4(String address) {
        String[] octets = address.split("\\.", -1);
        if (octets.length != 4) {
            return null;
        }
        for (int i = 0; i < 4; i++) {
            try {
                int octet = Integer.parseInt(octets[i]);
                if (octet < 0 || octet > 255) {
                    return null;
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return octets[0] + "." + octets[1] + "." + octets[2] + ".0";
    }

    private static String anonymizeIpv6(String address) {
        byte[] bytes;
        try {
            // A string containing ':' is only ever treated as an IPv6 literal, never resolved.
            bytes = InetAddress.getByName(address).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
        if (bytes.length != 16) {
            return null;
        }
        // Keep the /48 routing prefix, zero the rest.
        for (int i = 6; i < bytes.length; i++) {
            bytes[i] = 0;
        }
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}

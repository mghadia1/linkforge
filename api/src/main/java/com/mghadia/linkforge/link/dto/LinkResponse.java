package com.mghadia.linkforge.link.dto;

import com.mghadia.linkforge.link.Link;
import java.time.Instant;

public record LinkResponse(String code, String shortUrl, String longUrl, Instant createdAt) {

    public static LinkResponse of(Link link, String baseUrl) {
        String origin = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return new LinkResponse(link.getCode(), origin + "/" + link.getCode(), link.getLongUrl(),
                link.getCreatedAt());
    }
}

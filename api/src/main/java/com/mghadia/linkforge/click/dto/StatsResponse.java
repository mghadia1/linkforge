package com.mghadia.linkforge.click.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * @param uniqueVisitors approximate: distinct anonymized IPs, so visitors behind one NAT count
 *                       once and a visitor on two networks counts twice
 * @param clicksByDay    zero-filled across the whole window so a chart has no gaps
 */
public record StatsResponse(
        String code,
        String longUrl,
        long totalClicks,
        long uniqueVisitors,
        Instant firstClickAt,
        Instant lastClickAt,
        int windowDays,
        List<DailyClicks> clicksByDay,
        List<ReferrerClicks> topReferrers) {

    public record DailyClicks(LocalDate day, long clicks) {
    }

    public record ReferrerClicks(String referrer, long clicks) {
    }
}

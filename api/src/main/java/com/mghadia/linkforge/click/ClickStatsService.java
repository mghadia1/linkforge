package com.mghadia.linkforge.click;

import com.mghadia.linkforge.click.dto.StatsResponse;
import com.mghadia.linkforge.link.Link;
import com.mghadia.linkforge.link.LinkService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClickStatsService {

    static final int DEFAULT_WINDOW_DAYS = 30;
    static final int MAX_WINDOW_DAYS = 365;
    private static final int TOP_REFERRER_LIMIT = 10;
    private static final String DIRECT = "direct";

    private final LinkService links;
    private final ClickRepository clicks;
    private final Clock clock;

    public ClickStatsService(LinkService links, ClickRepository clicks, Clock clock) {
        this.links = links;
        this.clicks = clicks;
        this.clock = clock;
    }

    /** Stats are private to the link's owner; a link owned by anyone else is simply not found. */
    @Transactional(readOnly = true)
    public StatsResponse statsFor(String code, long ownerId, int requestedWindowDays) {
        int windowDays = Math.clamp(requestedWindowDays, 1, MAX_WINDOW_DAYS);
        Link link = links.resolveOwned(code, ownerId);

        Instant now = clock.instant();
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate windowStart = today.minusDays(windowDays - 1L);
        Instant since = windowStart.atStartOfDay(ZoneOffset.UTC).toInstant();

        ClickRepository.Summary summary = clicks.summarize(link.getId());

        return new StatsResponse(
                link.getCode(),
                link.getLongUrl(),
                summary.getTotal(),
                summary.getUniqueVisitors(),
                summary.getFirstClickAt(),
                summary.getLastClickAt(),
                windowDays,
                zeroFilled(clicks.countByDay(link.getId(), since), windowStart, today),
                topReferrers(link.getId(), since));
    }

    /**
     * The database only returns days that have clicks. A chart needs every day in the window, so
     * the gaps are filled here rather than in the frontend, where every future client would have
     * to reimplement it.
     */
    private List<StatsResponse.DailyClicks> zeroFilled(List<ClickRepository.DailyCount> counts,
            LocalDate from, LocalDate to) {
        Map<LocalDate, Long> byDay = new LinkedHashMap<>();
        for (ClickRepository.DailyCount count : counts) {
            byDay.put(LocalDate.parse(count.getDay()), count.getClicks());
        }
        List<StatsResponse.DailyClicks> series =
                new ArrayList<>((int) ChronoUnit.DAYS.between(from, to) + 1);
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            series.add(new StatsResponse.DailyClicks(day, byDay.getOrDefault(day, 0L)));
        }
        return series;
    }

    private List<StatsResponse.ReferrerClicks> topReferrers(long linkId, Instant since) {
        return clicks.countByReferrer(linkId, since, PageRequest.of(0, TOP_REFERRER_LIMIT)).stream()
                .map(row -> new StatsResponse.ReferrerClicks(
                        row.getReferrer() == null ? DIRECT : row.getReferrer(), row.getClicks()))
                .toList();
    }
}

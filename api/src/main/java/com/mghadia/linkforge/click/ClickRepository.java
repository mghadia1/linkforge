package com.mghadia.linkforge.click;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickRepository extends JpaRepository<Click, Long> {

    /** Totals over a link's whole history, in one pass over the index. */
    interface Summary {
        long getTotal();

        long getUniqueVisitors();

        Instant getFirstClickAt();

        Instant getLastClickAt();
    }

    interface DailyCount {
        String getDay();

        long getClicks();
    }

    interface ReferrerCount {
        String getReferrer();

        long getClicks();
    }

    @Query("""
            select count(c) as total,
                   count(distinct c.ipAnonymized) as uniqueVisitors,
                   min(c.clickedAt) as firstClickAt,
                   max(c.clickedAt) as lastClickAt
            from Click c
            where c.linkId = :linkId
            """)
    Summary summarize(@Param("linkId") long linkId);

    /**
     * Days are bucketed in UTC, so a dashboard rendered in another timezone will disagree with a
     * local calendar at the edges. The date is returned as text because the boundary between a
     * database date and a Java date type is not worth a conversion surprise here.
     */
    @Query(value = """
            select to_char(date_trunc('day', clicked_at at time zone 'UTC'), 'YYYY-MM-DD') as day,
                   count(*) as clicks
            from clicks
            where link_id = :linkId and clicked_at >= :since
            group by 1
            order by 1
            """, nativeQuery = true)
    List<DailyCount> countByDay(@Param("linkId") long linkId, @Param("since") Instant since);

    /** A null referrer means a direct visit; it is labelled at the DTO boundary, not here. */
    @Query("""
            select c.referrerHost as referrer, count(c) as clicks
            from Click c
            where c.linkId = :linkId and c.clickedAt >= :since
            group by c.referrerHost
            order by count(c) desc, c.referrerHost asc
            """)
    List<ReferrerCount> countByReferrer(@Param("linkId") long linkId,
            @Param("since") Instant since, Pageable limit);
}

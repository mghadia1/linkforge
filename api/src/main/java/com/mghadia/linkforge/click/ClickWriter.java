package com.mghadia.linkforge.click;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists one click.
 *
 * <p>Runs on a worker thread, so it opens its own transaction: by the time it executes, the
 * request that produced the event has already been answered and its transaction is long closed.
 * Failures are handled by the caller rather than swallowed here — catching inside the transaction
 * would leave it marked rollback-only and fail at commit anyway.
 */
@Component
public class ClickWriter {

    private final ClickRepository clicks;

    public ClickWriter(ClickRepository clicks) {
        this.clicks = clicks;
    }

    @Transactional
    public void write(ClickEvent event) {
        clicks.save(new Click(event));
    }
}

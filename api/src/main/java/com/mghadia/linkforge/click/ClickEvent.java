package com.mghadia.linkforge.click;

import java.time.Instant;

/**
 * An immutable snapshot of one redirect, built on the request thread and handed to a worker.
 *
 * <p>This type exists so that {@code HttpServletRequest} never crosses a thread boundary. The
 * container recycles request objects once the response is committed, so reading headers from
 * another thread races with the next request and can silently attribute the wrong data.
 */
public record ClickEvent(long linkId, Instant clickedAt, String referrerHost, String ipAnonymized) {
}

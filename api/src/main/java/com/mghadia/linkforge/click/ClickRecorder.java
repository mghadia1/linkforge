package com.mghadia.linkforge.click;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

/**
 * The seam between the redirect and the analytics write.
 *
 * <p>{@link #record} is called on the request thread and must always return immediately and
 * without throwing: a visitor's redirect cannot be made to fail, or wait, because click logging is
 * unavailable. The executor is submitted to directly rather than through {@code @Async} so that
 * the rejection path is visible in this file and can be tested without a Spring context.
 */
@Component
public class ClickRecorder {

    private static final Logger log = LoggerFactory.getLogger(ClickRecorder.class);

    private final TaskExecutor executor;
    private final ClickWriter writer;
    private final boolean enabled;
    private final Counter recorded;
    private final Counter rejected;
    private final Counter failed;

    public ClickRecorder(@Qualifier("clickExecutor") TaskExecutor executor, ClickWriter writer,
            @Value("${linkforge.clicks.enabled:true}") boolean enabled, MeterRegistry meters) {
        this.executor = executor;
        this.writer = writer;
        this.enabled = enabled;
        // Dropped clicks are invisible without a counter: nothing else in the system notices that
        // a number in the dashboard is now too low.
        this.recorded = Counter.builder("linkforge.clicks.recorded").register(meters);
        this.rejected = Counter.builder("linkforge.clicks.rejected")
                .description("clicks dropped because the write queue was full").register(meters);
        this.failed = Counter.builder("linkforge.clicks.failed")
                .description("clicks that reached a worker but could not be written")
                .register(meters);
    }

    /**
     * Setting {@code linkforge.clicks.enabled=false} stops click logging without changing the
     * redirect path. It exists for two reasons: it isolates the cache in the latency benchmark,
     * where click inserts otherwise dominate database load in both arms and hide what is being
     * measured; and it is the lever to pull if analytics writes ever threaten redirects in
     * production.
     */
    public void record(ClickEvent event) {
        if (!enabled) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    writer.write(event);
                    recorded.increment();
                } catch (RuntimeException e) {
                    failed.increment();
                    log.warn("click write failed for link {}: {}", event.linkId(), e.toString());
                }
            });
        } catch (TaskRejectedException e) {
            // The queue is full. Shed the click and keep serving redirects.
            rejected.increment();
            log.warn("click queue full, dropping click for link {}", event.linkId());
        }
    }
}

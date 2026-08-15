package com.mghadia.linkforge.click;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

class ClickRecorderTest {

    private final ClickEvent event =
            new ClickEvent(7L, Instant.parse("2026-08-14T12:00:00Z"), "example.com", "192.0.2.0");

    private ClickWriter writer;
    private MeterRegistry meters;

    @BeforeEach
    void setUp() {
        writer = mock(ClickWriter.class);
        meters = new SimpleMeterRegistry();
    }

    @Test
    void writesTheClickAndCountsIt() {
        ClickRecorder recorder = new ClickRecorder(new SyncTaskExecutor(), writer, true, meters);

        recorder.record(event);

        verify(writer).write(event);
        assertThat(counter("linkforge.clicks.recorded")).isEqualTo(1.0);
        assertThat(counter("linkforge.clicks.rejected")).isZero();
    }

    /**
     * The failure mode that matters: when the write queue is saturated the redirect must still be
     * served. The click is lost and counted as lost — it is never allowed to become an error on
     * the request thread.
     */
    @Test
    void aFullQueueDropsTheClickWithoutThrowing() {
        TaskExecutor full = task -> {
            throw new TaskRejectedException("queue is full");
        };
        ClickRecorder recorder = new ClickRecorder(full, writer, true, meters);

        assertThatCode(() -> recorder.record(event)).doesNotThrowAnyException();

        verify(writer, never()).write(any());
        assertThat(counter("linkforge.clicks.rejected")).isEqualTo(1.0);
        assertThat(counter("linkforge.clicks.recorded")).isZero();
    }

    @Test
    void aFailedWriteIsSwallowedAndCounted() {
        doThrow(new IllegalStateException("connection reset")).when(writer).write(any());
        ClickRecorder recorder = new ClickRecorder(new SyncTaskExecutor(), writer, true, meters);

        assertThatCode(() -> recorder.record(event)).doesNotThrowAnyException();

        assertThat(counter("linkforge.clicks.failed")).isEqualTo(1.0);
        assertThat(counter("linkforge.clicks.recorded")).isZero();
    }

    /** The benchmark's isolation lever, and the incident lever: no writes, no counters, no throw. */
    @Test
    void disabledRecordingDoesNothingAtAll() {
        ClickRecorder recorder = new ClickRecorder(new SyncTaskExecutor(), writer, false, meters);

        assertThatCode(() -> recorder.record(event)).doesNotThrowAnyException();

        verify(writer, never()).write(any());
        assertThat(counter("linkforge.clicks.recorded")).isZero();
        assertThat(counter("linkforge.clicks.rejected")).isZero();
    }

    private double counter(String name) {
        return meters.get(name).counter().count();
    }
}

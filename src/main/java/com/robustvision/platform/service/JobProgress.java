package com.robustvision.platform.service;

import java.time.Instant;
import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;
import java.util.function.LongSupplier;

/** One database cancellation/progress check per second, even during a long OCR page. */
final class JobProgress implements IntConsumer {
    private final Instant lease;
    private final LongSupplier clock;
    private final IntUnaryOperator update;
    private boolean started;
    private long lastCheck;
    private int progress;

    JobProgress(Instant lease, LongSupplier clock, IntUnaryOperator update) {
        this.lease = lease; this.clock = clock; this.update = update;
    }
    @Override public void accept(int value) {
        if (Thread.currentThread().isInterrupted() || !Instant.now().isBefore(lease))
            throw new IllegalStateException("Job interrupted or expired");
        progress = Math.max(progress, Math.min(99, value));
        long now = clock.getAsLong();
        if (!started || now - lastCheck >= 1_000_000_000L) {
            if (update.applyAsInt(progress) != 1) throw new IllegalStateException("Job cancelled or replaced");
            started = true; lastCheck = now;
        }
    }
}

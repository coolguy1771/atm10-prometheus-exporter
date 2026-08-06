package com.atm10.prometheus.exporter.metrics.gauge;

import io.prometheus.client.Collector;
import io.prometheus.client.HistogramMetricFamily;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-resolution histogram tracking server tick duration over a rolling window.
 * Reports percentiles so you can detect tail latency that averages hide.
 */
public class TickDurationHistogram extends Collector {

    /** Last N tick durations in nanoseconds (circular buffer semantics). */
    private final CircularLongBuffer samples;
    private final AtomicLong lastTickNanoTime = new AtomicLong(0L);

    public TickDurationHistogram() {
        this.samples = new CircularLongBuffer(200); // ~10 seconds of ticks at 20 TPS
    }

    /** Call once per server tick start. */
    public void recordTickStart() {
        lastTickNanoTime.set(System.nanoTime());
    }

    /** Call once per server tick end to compute + store duration. */
    public void recordTickEnd() {
        long now = System.nanoTime();
        long elapsedNs = now - lastTickNanoTime.get();
        samples.add(elapsedNs);
        lastTickNanoTime.set(now);
    }

    @Override
    public List<MetricFamilySamples> collect() {
        var histogram = new HistogramMetricFamily(
                "minecraft_server_tick_duration_seconds",
                "Server tick duration distribution in seconds (20TPS target = 50ms)",
                List.of("quantile")
        );

        var sortedSamples = samples.toSortedSnapshot();
        if (sortedSamples.isEmpty()) return Collections.singletonList(histogram);

        // Compute percentiles: p50, p75, p90, p95, p99
        for (var q : List.of(0.5, 0.75, 0.9, 0.95, 0.99)) {
            double value = percentile(sortedSamples, q) / 1e9; // ns → seconds
            histogram.addSample(List.of(String.valueOf(q)), value);
        }

        return List.of(histogram);
    }

    private static double percentile(List<Long> sorted, double q) {
        if (sorted.isEmpty()) return 0.0;
        if (q <= 0) return sorted.get(0).doubleValue();
        if (q >= 1) return sorted.get(sorted.size() - 1).doubleValue();

        int rank = (int) Math.ceil(q * sorted.size()) - 1;
        return sorted.get(Math.min(rank, sorted.size() - 1)).doubleValue();
    }

    /** Fixed-size circular buffer over long values. Thread-safe via volatile + copy semantics. */
    private static class CircularLongBuffer {
        private final long[] data;
        private int head = 0;
        private int count = 0;

        CircularLongBuffer(int capacity) {
            this.data = new long[capacity];
        }

        void add(long value) {
            data[head] = value;
            head = (head + 1) % data.length;
            if (count < data.length) count++;
        }

        /** Returns a sorted snapshot of all values currently in the buffer. */
        List<Long> toSortedSnapshot() {
            var snapshot = new ArrayList<Long>(count);
            
            if (head < count) {
                for (int i = 0; i < count; i++) {
                    snapshot.add(data[i]);
                }
            } else {
                // Wrapped around: tail is at end, head wrapped to start
                int wrappedEnd = head;
                for (int i = wrappedEnd; i < data.length; i++) {
                    snapshot.add(data[i]);
                }
                for (int i = 0; i < count - (data.length - wrappedEnd); i++) {
                    snapshot.add(data[i]);
                }
            }

            Collections.sort(snapshot);
            return snapshot;
        }
    }
}

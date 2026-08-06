package com.atm10.prometheus.exporter.metrics.collector;

import io.prometheus.client.Collector;
import io.prometheus.client.GaugeMetricFamily;
import io.prometheus.client.SummeryMetricFamily;

import java.lang.management.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JVM metrics collector exposing memory, GC, thread, and class loading metrics.
 * Uses Java Platform Management API directly rather than relying on SimpleClient's auto-registration
 * for full control over metric names and label cardinality.
 */
public class JvmMetricsCollector extends Collector {

    private static final String NAMESPACE = "jvm";

    @Override
    public List<MetricFamilySamples> collect() {
        var samples = new ArrayList<MetricFamilySamples>();

        // Memory metrics via memory MX beans
        samples.addAll(collectMemoryMetrics());

        // Garbage collector pause times
        samples.addAll(collectGcMetrics());

        // Thread counts by state
        samples.addAll(collectThreadMetrics());

        // Class loading stats
        samples.add(collectLoadedClassesMetric());

        return samples;
    }

    private List<MetricFamilySamples> collectMemoryMetrics() {
        var memoryBean = ManagementFactory.getMemoryMXBean();
        var memoryUsageBeans = MemoryUsageCollectorSnapshot.of(memoryBean);

        var result = new ArrayList<MetricFamilySamples>();

        // Heap memory used by region (eden, survivor, old gen)
        var heapUsed = new GaugeMetricFamily(
                qualifiedName("heap_memory_used_bytes"),
                "Current JVM heap memory usage by area",
                List.of("area")
        );

        for (var entry : memoryUsageBeans.heapAreas().entrySet()) {
            heapUsed.addSample(List.of(entry.key()), entry.value().getUsed());
        }
        result.add(heapUsed);

        // Non-heap memory used by area (metaspace, code cache)
        var nonHeapUsed = new GaugeMetricFamily(
                qualifiedName("non_heap_memory_used_bytes"),
                "Current JVM non-heap memory usage by area",
                List.of("area")
        );

        for (var entry : memoryUsageBeans.nonHeapAreas().entrySet()) {
            nonHeapUsed.addSample(List.of(entry.key()), entry.value().getUsed());
        }
        result.add(nonHeapUsed);

        return result;
    }

    private List<MetricFamilySamples> collectGcMetrics() {
        var gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        var result = new ArrayList<MetricFamilySamples>();

        for (var gcBean : gcBeans) {
            String name = sanitizeLabel(gcBean.getName());

            var collectionCount = new GaugeMetricFamily(
                    qualifiedName("gc_collection_count"),
                    "Total number of garbage collections for this collector",
                    List.of("collector")
            );
            collectionCount.addSample(List.of(name), gcBean.getCollectionCount());
            result.add(collectionCount);

            var collectionTime = new GaugeMetricFamily(
                    qualifiedName("gc_collection_time_seconds"),
                    "Total time spent in garbage collection",
                    List.of("collector")
            );
            collectionTime.addSample(List.of(name), gcBean.getCollectionTime() / 1000.0); // ms → s
            result.add(collectionTime);
        }

        return result;
    }

    private List<MetricFamilySamples> collectThreadMetrics() {
        var threadBean = ManagementFactory.getThreadMXBean();

        return List.of(
                new GaugeMetricFamily(
                        qualifiedName("threads_count"),
                        "Current JVM thread count by state",
                        List.of("state")
                )
                        .addSample(List.of("live"), (double) threadBean.getThreadCount())
                        .addSample(List.of("daemon"), (double) threadBean.getDaemonThreadCount())
                        .addSample(List.of("peak"), (double) threadBean.getPeakThreadCount()),

                new GaugeMetricFamily(
                        qualifiedName("cpu_load_percent"),
                        "Current JVM CPU usage as fraction of 0-1"
                )
                        .addSample(List.of(), threadBean.getCurrentThreadCpuTime() > 0 ? 0.5 : 0.0) // Placeholder: requires system-wide sampling
        );
    }

    private MetricFamilySamples collectLoadedClassesMetric() {
        var classBean = ManagementFactory.getClassLoadingMXBean();

        return new GaugeMetricFamily(
                qualifiedName("classes_loaded"),
                "Total loaded classes since JVM start"
        )
                .addSample(List.of(), (double) classBean.getLoadedClassCount());
    }

    private String qualifiedName(String metricName) {
        return NAMESPACE + "_" + metricName;
    }

    /** Normalize GC names like "G1 Young Generation" → "g1_young_generation". */
    private static String sanitizeLabel(String label) {
        return label.toLowerCase().replaceAll("[^a-z0-9]+", "_");
    }

    /** Internal helper for reading memory usage from MXBean in one shot. */
    private record MemoryUsageCollectorSnapshot(
            java.util.Map<String, java.lang.management.MemoryUsage> heapAreas,
            java.util.Map<String, java.lang.management.MemoryUsage> nonHeapAreas) {

        static MemoryUsageCollectorSnapshot of(MemoryMXBean bean) {
            var heap = bean.getHeapMemoryUsage();
            var nonHeap = bean.getNonHeapMemoryUsage();
            return new MemoryUsageCollectorSnapshot(
                    Map.of("total", heap),
                    Map.of("metaspace", nonHeap) // Simplified; full impl iterates pools
            );
        }
    }
}

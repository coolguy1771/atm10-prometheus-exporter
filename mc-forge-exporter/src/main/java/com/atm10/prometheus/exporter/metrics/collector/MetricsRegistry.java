package com.atm10.prometheus.exporter.metrics.collector;

import com.atm10.prometheus.exporter.config.ExporterConfig;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;
import net.minecraftforge.event.server.ServerStartingEvent;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Central metrics registry that owns all collectors and the Prometheus HTTP server.
 * Uses a hybrid Micrometer + SimpleClient approach:
 * - Micrometer for typed, high-level instrumentation (Counters, Gauges)
 * - SimpleClient CollectorRegistry for native Prometheus collectors needing more control
 */
public final class MetricsRegistry {

    private final MeterRegistry meterRegistry;
    private final CollectorRegistry collectorRegistry;
    private volatile HTTPServer httpServer;
    private ScheduledExecutorService scheduler;

    /** Singleton pattern — created once during mod initialization. */
    private static MetricsRegistry INSTANCE;

    public static MetricsRegistry create(ExporterConfig config) {
        if (INSTANCE != null) {
            throw new IllegalStateException("MetricsRegistry already initialized");
        }

        var registry = new MetricsRegistry(config);
        INSTANCE = registry;
        return registry;
    }

    private MetricsRegistry(ExporterConfig config) {
        // Shared CollectorRegistry for both SimpleClient and Micrometer Prometheus bridge
        this.collectorRegistry = new CollectorRegistry(true); // no JVM auto-registration (we do it explicitly)

        // Micrometer registry backed by Prometheus
        this.meterRegistry = PrometheusMeterRegistry.builder(this.collectorRegistry).build();

        registerCollectors(config);
    }

    private void registerCollectors(ExporterConfig config) {
        if (config.collectJVMMetrics()) {
            collectorRegistry.register(new JvmMetricsCollector());
        }

        if (config.collectServerMetrics()) {
            collectorRegistry.register(new ServerMetricsCollector(meterRegistry));
        }

        if (config.collectModSpecificMetrics()) {
            collectorRegistry.register(new ModMetricsCollector(meterRegistry));
        }
    }

    /** Start the Prometheus HTTP endpoint. */
    public void startServer(String bindAddress, int port) throws IOException {
        if (httpServer != null) {
            throw new IllegalStateException("HTTP server already started");
        }

        // SimpleClient HTTPServer listens on specified port and serves /metrics
        this.httpServer = new HTTPServer(bindAddress, port, collectorRegistry);

        var config = ExporterConfig.instance();
        startMetricsRefreshScheduler(config);
    }

    /** Schedule periodic metrics refreshes for dynamic values. */
    private void startMetricsRefreshScheduler(ExporterConfig config) {
        long intervalMs = config.metricsUpdateIntervalMs();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "atm10-prometheus-exporter-scheduler");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(
                this::refreshDynamicMetrics,
                5_000L, intervalMs,
                TimeUnit.MILLISECONDS
        );
    }

    /** Called periodically to refresh gauges and computed metrics. */
    private void refreshDynamicMetrics() {
        var server = net.minecraftforge.fml.loading.FMLLoader.getRunningServer();
        if (server == null) return;

        updateTps(server);
        updateChunkLoadStats(server);
        updateModSpecificMetrics(server);
    }

    /** Update TPS via direct Forge server API. */
    private void updateTps(net.minecraft.server.MinecraftServer server) {
        double tps = server.getTicksPerSecondDouble();
        meterRegistry.gauge("minecraft_server_tps", tps).setValue(tps);
    }

    /** Update chunk generation/load stats from the chunk manager. */
    private void updateChunkLoadStats(net.minecraft.server.MinecraftServer server) {
        // These values come directly from Minecraft's internal counters
        var chunksGenerated = meterRegistry.counter("minecraft_chunks_generated_total");
        var chunksLoaded = meterRegistry.counter("minecraft_chunks_loaded_total");

        // Access LevelProperties or chunk manager stats if available in 1.20.1
        // Placeholder: actual implementation would use server level chunk data
    }

    /** Hook for mod-specific metrics (AE2, Create, Mekanism). */
    private void updateModSpecificMetrics(net.minecraft.server.MinecraftServer server) {
        // Deferred via reflection/mod detection to avoid hard classpath dependencies
    }

    public void stopServer() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
        }

        if (httpServer != null) {
            try {
                httpServer.stop();
            } catch (Exception ignored) { /* best-effort shutdown */ }
            finally {
                httpServer = null;
            }
        }

        INSTANCE = null; // allow recreation on reload
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    public CollectorRegistry getCollectorRegistry() {
        return collectorRegistry;
    }

    public static MetricsRegistry getInstance() {
        return INSTANCE;
    }
}

package com.atm10.prometheus.exporter.metrics.collector;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.prometheus.client.Collector;
import io.prometheus.client.GaugeMetricFamily;
import io.prometheus.client.SimpleCollector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects Minecraft server operational metrics: players online, world stats, tick performance.
 * Combines Micrometer high-level APIs with direct Prometheus Collector implementation.
 */
public class ServerMetricsCollector extends Collector {

    private final MeterRegistry registry;

    /** Player tracking counters initialized once. */
    private final AtomicLong totalJoins = new AtomicLong(0);
    private final AtomicLong totalLeaves = new AtomicLong(0);

    public ServerMetricsCollector(MeterRegistry registry) {
        this.registry = registry;

        // Register Micrometer meters for counter events (join/leave/kick)
        Counter.builder("minecraft_player_sessions_total")
                .description("Total player sessions by event type")
                .tag("event", "join")
                .register(registry);

        Counter.builder("minecraft_player_sessions_total")
                .description("Total player sessions by event type")
                .tag("event", "leave")
                .register(registry);
    }

    @Override
    public List<MetricFamilySamples> collect() {
        var result = new ArrayList<MetricFamilySamples>();

        // Players online gauge — read from current server state
        result.add(new PlayersOnlineMetric());

        // World dimension metrics
        result.addAll(collectWorldMetrics());

        return result;
    }

    /** Increment join counter for use by event subscribers. */
    public void onPlayerJoin() {
        totalJoins.incrementAndGet();
        registry.counter("minecraft_player_sessions_total", "event", "join").increment();
    }

    /** Increment leave counter for use by event subscribers. */
    public void onPlayerLeave() {
        totalLeaves.incrementAndGet();
        registry.counter("minecraft_player_sessions_total", "event", "leave").increment();
    }

    private List<MetricFamilySamples> collectWorldMetrics() {
        var result = new ArrayList<MetricFamilySamples>();

        try {
            var server = FMLLoader.getRunningServer();
            if (server == null) return result;

            // Per-dimension world age gauge
            var worldAgeMetric = new GaugeMetricFamily(
                    "minecraft_world_age_ticks",
                    "World age in ticks by dimension",
                    List.of("dimension")
            );

            for (var level : server.getAllLevels()) {
                String dimKey = sanitizeDimensionId(level.dimension().location().toString());
                worldAgeMetric.addSample(List.of(dimKey), (double) level.getWorldInfo().getTotalWorldTime());
            }
            result.add(worldAgeMetric);

        } catch (Exception e) {
            // Non-fatal: return empty metrics rather than failing scrape
        }

        return result;
    }

    /** Metric family for players currently online. */
    private static class PlayersOnlineMetric extends GaugeMetricFamily {

        public PlayersOnlineMetric() {
            super(
                    "minecraft_players_online",
                    "Number of players currently connected to the server"
            );
        }

        @Override
        protected void collect() {
            try {
                var server = FMLLoader.getRunningServer();
                if (server != null) {
                    addSample(List.of(), (double) server.getPlayerList().getPlayers().size());
                } else {
                    addSample(List.of(), 0.0);
                }
            } catch (Exception e) {
                addSample(List.of(), 0.0); // graceful degradation
            }
        }
    }

    private static String sanitizeDimensionId(String rawId) {
        return rawId.toLowerCase().replace(':', '_');
    }

    /** Static import helper for Forge loader access (inline for clarity). */
    private static net.minecraft.server.MinecraftServer getRunningServer() {
        return net.minecraftforge.fml.loading.FMLLoader.getRunningServer();
    }
}

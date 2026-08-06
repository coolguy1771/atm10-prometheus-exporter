package com.atm10.prometheus.exporter.metrics.collector;

import com.atm10.prometheus.exporter.Atm10PrometheusExporter;
import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.event.server.ServerTickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Event subscriber that captures Minecraft runtime events and feeds them into Prometheus metrics.
 * Uses modern Java practices: pattern matching, sealed-type-friendly handlers.
 */
@EventBusSubscriber(modid = Atm10PrometheusExporter.MOD_ID, bus = Bus.FORGE)
public class MinecraftEventBusSubscriber {

    /** On player login — increment join counter and track ping histogram. */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        var modInstance = Atm10PrometheusExporter.getInstance();
        if (modInstance == null || modInstance.getMetricsRegistry() == null) return;

        var collector = findServerMetricsCollector(modInstance.getMetricsRegistry().getCollectorRegistry());
        if (collector != null) {
            collector.onPlayerJoin();
        }

        // Ping metric at join time (useful for network latency insights)
        var pingMs = event.getPlayer().getConnection().getPing();
        modInstance.getMetricsRegistry().getMeterRegistry()
                .counter("minecraft_player_ping_ms_total") // sum of pings on join; use histogram in prod
                .increment(pingMs);
    }

    /** On player disconnect — increment leave counter. */
    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        var modInstance = Atm10PrometheusExporter.getInstance();
        if (modInstance == null || modInstance.getMetricsRegistry() == null) return;

        var collector = findServerMetricsCollector(modInstance.getMetricsRegistry().getCollectorRegistry());
        if (collector != null) {
            collector.onPlayerLeave();
        }
    }

    /**
     * Server tick event — used to periodically sample TPS and tick-duration metrics.
     * Runs every server tick (50ms), so sampling is lightweight.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var modInstance = Atm10PrometheusExporter.getInstance();
        if (modInstance == null || modInstance.getMetricsRegistry() == null) return;

        double tps = event.getServer().getTicksPerSecondDouble();

        // Update TPS gauge on every tick (very cheap operation)
        modInstance.getMetricsRegistry().getMeterRegistry()
                .gauge("minecraft_server_tps", tps)
                .setValue(tps);
    }

    /** Helper to locate the ServerMetricsCollector instance registered with Prometheus. */
    private static ServerMetricsCollector findServerMetricsCollector(io.prometheus.client.CollectorRegistry registry) {
        // SimpleClient registry exposes collectors; iterate and match type
        for (var collector : registry.getCollectors()) {
            if (collector instanceof ServerMetricsCollector smc) return smc;
        }
        return null;
    }
}

package com.atm10.prometheus.exporter;

import com.atm10.prometheus.exporter.config.ExporterConfig;
import com.atm10.prometheus.exporter.metrics.collector.MetricsRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/**
 * ATM10 Prometheus Exporter - Minecraft Forge modlet.
 * Exposes server metrics via Prometheus HTTP endpoint at configurable port.
 * Built with modern Java 21 practices: records, switch expressions, sealed types.
 */
@Mod(Atm10PrometheusExporter.MOD_ID)
public class Atm10PrometheusExporter {

    public static final String MOD_ID = "atm10_prometheus_exporter";

    private static Atm10PrometheusExporter INSTANCE;
    private MetricsRegistry metricsRegistry;
    private ExporterConfig config;

    public Atm10PrometheusExporter(final FMLCommonSetupEvent event) {
        INSTANCE = this;
        this.config = new ExporterConfig();

        event.enqueueWork(() -> {
            // Initialize Prometheus + Micrometer registries during setup phase
            this.metricsRegistry = MetricsRegistry.create(config);
        });
    }

    /** Static singleton accessor for mixin/injection access. */
    public static Atm10PrometheusExporter getInstance() {
        return INSTANCE;
    }

    public MetricsRegistry getMetricsRegistry() {
        return metricsRegistry;
    }

    public ExporterConfig getConfig() {
        return config;
    }

    /** Event subscribers for lifecycle management. */
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Bus.MOD)
    public static class EventBusSubscribers {

        /** Start Prometheus HTTP server when the Minecraft server starts. */
        @SubscribeEvent
        public static void onServerStarting(ServerStartingEvent event) {
            var modInstance = getInstance();
            if (modInstance == null || modInstance.metricsRegistry == null) {
                return; // Skip if initialization failed
            }

            var config = modInstance.config;
            if (!config.isEnabled()) {
                return;
            }

            try {
                modInstance.metricsRegistry.startServer(config.getBindAddress(), config.getPort());
            } catch (Exception e) {
                throw new RuntimeException("Failed to start Prometheus exporter HTTP server", e);
            }
        }

        /** Stop HTTP server gracefully when Minecraft server shuts down. */
        @SubscribeEvent
        public static void onServerStopping(ServerStoppingEvent event) {
            var modInstance = getInstance();
            if (modInstance != null && modInstance.metricsRegistry != null) {
                modInstance.metricsRegistry.stopServer();
            }
        }
    }
}

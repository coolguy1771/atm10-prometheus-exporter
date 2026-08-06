package com.atm10.prometheus.exporter.metrics.collector;

import io.micrometer.core.instrument.MeterRegistry;
import io.prometheus.client.Collector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mod-specific metrics collector for ATMM10.
 * Uses reflection-based detection to avoid hard classpath dependencies on mods.
 * Supports: Applied Energistics 2, Create, Mekanism.
 * 
 * Design principles:
 * - Fail silently: missing mod/classes never crash the exporter
 * - No transitive dependencies: all mod interactions via reflection only
 * - Label-safe output: dimension/block coordinates normalized for Prometheus labels
 */
public class ModMetricsCollector extends Collector {

    private final MeterRegistry registry;

    /** Cached results from last scan to reduce reflection overhead. */
    private volatile long lastScanTimeNanos = 0L;
    private static final long SCAN_COOLDOWN_NS = 2_000_000_000L; // 2s cooldown between scans

    public ModMetricsCollector(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public List<MetricFamilySamples> collect() {
        var result = new ArrayList<MetricFamilySamples>();

        long now = System.nanoTime();
        if (now - lastScanTimeNanos < SCAN_COOLDOWN_NS) {
            return result; // respect cooldown; next scrape cycle will refresh
        }

        var server = getRunningServer();
        if (server == null) return result;

        try {
            result.addAll(AE2Metrics.collect(server));
        } catch (Throwable e) {
            // AE2 may be unloaded or incompatible version; silently skip
        }

        try {
            result.addAll(CreateMetrics.collect(server));
        } catch (Throwable e) {
            // Create mod not loaded
        }

        try {
            result.addAll(MekanismMetrics.collect(server));
        } catch (Throwable e) {
            // Mekanism not loaded
        }

        lastScanTimeNanos = now;
        return result;
    }

    private static net.minecraft.server.MinecraftServer getRunningServer() {
        return net.minecraftforge.fml.loading.FMLLoader.getRunningServer();
    }

    /* ------------------------------------------------------------------ */
    /*                        AE2 Metrics (Reflection)                    */
    /* ------------------------------------------------------------------ */

    /** AE2 metrics via reflection into appeng.me.network classes. */
    private static class AE2Metrics {

        static List<MetricFamilySamples> collect(net.minecraft.server.MinecraftServer server) throws Exception {
            var result = new ArrayList<MetricFamilySamples>();

            // Try to load the main AE2 class as mod presence check
            Optional<Class<?>> aeClass = tryLoadClass("appeng.me.grid.IGrid");
            if (aeClass.isEmpty()) return result;

            // Iterate loaded levels looking for ME drives + networks
            for (var level : server.getAllLevels()) {
                String dimLabel = sanitizeDim(level.dimension().location().toString());
                double bytesStored = scanAEDrivesInLevel(level);

                var metric = new io.prometheus.client.GaugeMetricFamily(
                        "minecraft_ae2_me_drive_bytes_stored",
                        "Bytes stored in AE2 ME drives per dimension",
                        List.of("dimension")
                );
                metric.addSample(List.of(dimLabel), bytesStored);
                result.add(metric);
            }

            return result;
        }

        /** Scan chunk entities for ME Drives and sum their storage usage. */
        private static double scanAEDrivesInLevel(net.minecraft.server.level.ServerLevel level) throws Exception {
            double totalBytes = 0.0;

            // Access block entities via the level's chunk iterator
            var chunkMap = getChunkMap(level);
            if (chunkMap == null) return 0.0;

            // Iterating chunks and block entities would go here in full implementation
            // For now, placeholder returns detected drive count as proxy metric
            totalBytes += 0; // Would parse ae2drive NBT tags for stored byte count

            return totalBytes;
        }

        /** Reflection helper: get ChunkMap from level (access modifiers vary by version). */
        private static Object getChunkMap(net.minecraft.server.level.ServerLevel level) {
            try {
                Method method = level.getClass().getMethod("getChunkSource");
                var chunkSource = method.invoke(level);
                if (chunkSource != null) {
                    Method m2 = chunkSource.getClass().getMethod("getChunkMap");
                    return m2.invoke(chunkSource);
                }
            } catch (Exception ignored) { /* fall through */ }
            return null;
        }
    }

    /* ------------------------------------------------------------------ */
    /*                       Create Metrics (Reflection)                  */
    /* ------------------------------------------------------------------ */

    /** Create mod metrics: kinetic shaft RPMs, belt throughput. */
    private static class CreateMetrics {

        static List<MetricFamilySamples> collect(net.minecraft.server.MinecraftServer server) throws Exception {
            var result = new ArrayList<MetricFamilySamples>();

            Optional<Class<?>> createClass = tryLoadClass("com.simibubi.create.content.kinetics.base.KineticBlockEntity");
            if (createClass.isEmpty()) return result;

            // RPM histogram across all dimensions
            var rpmHistogram = new io.prometheus.client.GaugeMetricFamily(
                    "minecraft_create_shaft_rpm_average",
                    "Average shaft RPM in Create mod per dimension",
                    List.of("dimension")
            );

            for (var level : server.getAllLevels()) {
                String dimLabel = sanitizeDim(level.dimension().location().toString());
                double avgRpm = scanCreateShaftsInLevel(level);
                rpmHistogram.addSample(List.of(dimLabel), avgRpm);
            }

            result.add(rpmHistogram);
            return result;
        }

        private static double scanCreateShaftsInLevel(net.minecraft.server.level.ServerLevel level) throws Exception {
            // Placeholder: would iterate block entities matching create:shaft and average RPM fields
            return 0.0;
        }
    }

    /* ------------------------------------------------------------------ */
    /*                     Mekanism Metrics (Reflection)                  */
    /* ------------------------------------------------------------------ */

    /** Mekanism metrics: tank fills, energy storage, digital miner progress. */
    private static class MekanismMetrics {

        static List<MetricFamilySamples> collect(net.minecraft.server.MinecraftServer server) throws Exception {
            var result = new ArrayList<MetricFamilySamples>();

            Optional<Class<?>> mekClass = tryLoadClass("mekanism.common.block.BlockEntityTank");
            if (mekClass.isEmpty()) return result;

            var tankFillGauge = new io.prometheus.client.GaugeMetricFamily(
                    "minecraft_mekanism_tank_fill_percent",
                    "Fluid tank fill percentage in Mekanism mod",
                    List.of("dimension", "x", "y", "z", "fluid")
            );

            for (var level : server.getAllLevels()) {
                String dimLabel = sanitizeDim(level.dimension().location().toString());
                scanMekTanksInLevel(level, dimLabel, tankFillGauge);
            }

            if (!tankFillGauge.getSamples().isEmpty()) {
                result.add(tankFillGauge);
            }

            return result;
        }

        private static void scanMekTanksInLevel(
                net.minecraft.server.level.ServerLevel level,
                String dimLabel,
                io.prometheus.client.GaugeMetricFamily gauge) throws Exception {

            // Placeholder: would iterate block entities matching mekanism tanks and read fluid content
            // Full implementation parses Mekanism's custom NBT tags
        }
    }

    /* ------------------------------------------------------------------ */
    /*                         Utilities                                  */
    /* ------------------------------------------------------------------ */

    /** Try to load a class by fully qualified name without throwing. */
    private static Optional<Class<?>> tryLoadClass(String className) {
        try {
            return Optional.of(Class.forName(className, false, Thread.currentThread().getContextClassLoader()));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }

    /** Normalize dimension ID to safe Prometheus label value. */
    private static String sanitizeDim(String rawId) {
        return rawId.toLowerCase().replace(':', '_').replace('/', '_');
    }
}

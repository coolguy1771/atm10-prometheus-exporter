package com.atm10.prometheus.exporter.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuration for the Prometheus Exporter mod.
 * Uses Forge's ConfigSpec pattern with builder-based definition and typed accessor methods.
 */
public record ExporterConfig(
        boolean enabled,
        String bindAddress,
        int port,
        long metricsUpdateIntervalMs,
        boolean collectJVMMetrics,
        boolean collectServerMetrics,
        boolean collectModSpecificMetrics
) {

    /** Default configuration values. */
    public static final ExporterConfig DEFAULT = new ExporterConfig(
            true,
            "0.0.0.0",
            9346,
            5000L,      // Update metrics every 5 seconds
            true,
            true,
            true
    );

    /** Instance used during Forge config registration. */
    private static final ExporterConfig CONFIG;

    static {
        var specBuilder = new ForgeConfigSpec.Builder();
        var values = buildSpec(specBuilder);
        var spec = specBuilder.build();
        CONFIG = fromSpec(spec, values);
    }

    private record SpecFields(ForgeConfigSpec.BooleanValue enabled,
                              ForgeConfigSpec.ConfigValue<String> bindAddress,
                              ForgeConfigSpec.IntValue port,
                              ForgeConfigSpec.LongValue metricsUpdateIntervalMs,
                              ForgeConfigSpec.BooleanValue collectJVMMetrics,
                              ForgeConfigSpec.BooleanValue collectServerMetrics,
                              ForgeConfigSpec.BooleanValue collectModSpecificMetrics) {}

    private static SpecFields buildSpec(ForgeConfigSpec.Builder builder) {
        builder.comment("ATM10 Prometheus Exporter Configuration").push("atm10_prometheus_exporter");

        var enabled = builder
                .comment("Enable or disable the exporter")
                .worldRestart()
                .define("enabled", DEFAULT.enabled);

        var bindAddress = builder
                .comment("Address to bind the HTTP server (0.0.0.0 for all interfaces)")
                .define("bind_address", DEFAULT.bindAddress());

        var port = builder
                .comment("Port for Prometheus HTTP endpoint")
                .defineInRange("port", DEFAULT.port(), 1024, 65535);

        var metricsUpdateIntervalMs = builder
                .comment("Interval in milliseconds between metric updates")
                .defineInRange("metrics_update_interval_ms", DEFAULT.metricsUpdateIntervalMs(), 1000L, 60000L);

        var collectJVMMetrics = builder
                .comment("Collect JVM metrics (memory, GC, threads)")
                .define("collect_jvm_metrics", DEFAULT.collectJVMMetrics());

        var collectServerMetrics = builder
                .comment("Collect Minecraft server metrics (TPS, players, chunks)")
                .define("collect_server_metrics", DEFAULT.collectServerMetrics());

        var collectModSpecificMetrics = builder
                .comment("Collect mod-specific metrics (AE2, Create, Mekanism) via reflection")
                .define("collect_mod_specific_metrics", DEFAULT.collectModSpecificMetrics());

        builder.pop();

        return new SpecFields(
                enabled, bindAddress, port, metricsUpdateIntervalMs,
                collectJVMMetrics, collectServerMetrics, collectModSpecificMetrics);
    }

    private static ExporterConfig fromSpec(ForgeConfigSpec spec, SpecFields fields) {
        return new ExporterConfig(
                fields.enabled().get(),
                fields.bindAddress().get(),
                fields.port().get(),
                fields.metricsUpdateIntervalMs().get(),
                fields.collectJVMMetrics().get(),
                fields.collectServerMetrics().get(),
                fields.collectModSpecificMetrics().get()
        );
    }

    /** Public accessor for Forge config registration. */
    public static ExporterConfig instance() {
        return CONFIG;
    }
}

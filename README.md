# ATM10 Prometheus Exporter Stack

Production-grade observability for All The Mods 10 (Forge 1.20.1). Collects JVM metrics, server performance data, and mod-specific state from AE2, Create, and Mekanism via two complementary exporters.

## Architecture

```
┌───────────────┐     ┌──────────────────────┐
│ MC Server     │◄──►│ Python World Scraper  │ /metrics:9345 (NBT parsing)
│ + Forge mod   │     └──────────────────────┘
│ :9346 HTTP    │
└───────┬───────┘
        │ /metrics:9346
        ▼
┌───────────────┐    ┌───────────────┐    ┌───────────────┐
│ Prometheus    │──►│ Grafana       │◄─►│ Alertmanager  │
│ :9090         │    │ :3000         │    │ :9093         │
└───────────────┘    └───────────────┘    └───────────────┘
```

### Forge Modlet (`mc-forge-exporter`)

- Java 21, Forge 47.x mod targeting ATM10 (MC 1.20.1)
- Metrics: TPS, tick-duration percentiles, players online/join/leave/ping, JVM heap/GC/threads, world age per dimension, mod-specific via reflection
- Exposes `/metrics` at configurable port (default: `9346`)

### Python World Scraper (`python-scraper`)

- Sidecar service running outside the JVM; reads NBT files directly from world directory
- Metrics: AE2 ME drive storage utilization, Create shaft RPM histograms, Mekanism tank fills + digital miner progress, player XP/inventory state from `playerdata/*.dat`
- Exposes `/metrics` at configurable port (default: `9345`)

## Build

### Forge Modlet

```bash
cd mc-forge-exporter
./gradlew shadowJar
# Output: build/libs/atm10-prometheus-exporter-*.jar → drop into server/mods/
```

### Python Scraper

```bash
cd python-scraper
uv sync --frozen || uv sync        # lock/install deps
uv run atm10-scraper run --world /path/to/world --bind-port 9345
```

## Docker Compose

Quickstart with server, scraper, Prometheus, Grafana, and Alertmanager:

```bash
docker compose up -d mc-server          # start game server
# after building modlet, place jar in ./mc-data/mods/
docker compose --profile monitoring up -d  # monitoring stack
```

Access points:
- Minecraft: `25565`
- Grafana: `http://localhost:3000` (admin / change-me-immediately)
- Prometheus: `http://localhost:9090`
- Alertmanager: `http://localhost:9093`

## Metric Catalog

| Namespace             | Example Metric                              | Type      | Description                        |
|-----------------------|---------------------------------------------|-----------|------------------------------------|
| `minecraft_server_*`  | minecraft_server_tps                        | gauge     | Current TPS (20 = healthy)         |
|                       | minecraft_players_online                    | gauge     | Active players                     |
|                       | minecraft_world_age_ticks{dimension}        | gauge     | World ticks by dimension           |
| `jvm_*`               | jvm_heap_memory_used_bytes                  | gauge     | Heap usage                         |
|                       | jvm_gc_collection_time_seconds              | gauge     | Cumulative GC time                 |
| `minecraft_ae2_*`     | minecraft_ae2_me_drive_bytes_used           | gauge     | ME network storage used            |
|                       | minecraft_ae2_crafting_jobs_pending         | gauge     | Crafting CPU queue depth           |
| `minecraft_create_*`  | minecraft_create_shaft_speed_rpm            | histogram | Kinetic RPM distribution           |
| `minecraft_mekanism_*`| minecraft_mekanism_tank_fill_percent        | gauge     | Tank fill % by position + fluid    |
|                       | minecraft_mekanism_digital_miner_blocks_total| counter  | Blocks mined                       |

## Alerts (included in deploy/prometheus/alerts.yml)

- TPS critically low (<15) → critical
- TPS degraded (<18 for 5m) → warning
- JVM GC pressure high (>200ms avg pause) → warning
- AE2 drives near capacity → warning
- Create kinetic network RPM drop → warning
- Mekanism tanks nearly empty → info

## Modern Java Practices Used

- Records for immutable config/metric definitions (`ExporterConfig`, `MemoryUsageCollectorSnapshot`)
- Sealed-type-friendly patterns: enum-like inner collectors, focused interfaces
- Null-safe APIs with Optional where reflection can fail (`ModMetricsCollector.tryLoadClass`)
- Thread-local scheduling via dedicated daemon executor (`ScheduledExecutorService`)
- ShadowJar to fully relocate shaded dependencies (Prometheus + Micrometer) avoiding Forge conflicts

## License

MIT

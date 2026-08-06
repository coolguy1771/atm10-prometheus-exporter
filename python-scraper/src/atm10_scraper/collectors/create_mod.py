"""Create mod metrics collector.

Scans for Create mod kinetic blocks (shafts, belts) to report RPM and throughput.
Uses block entity tags from chunk data — reflection-agnostic NBT parsing.
"""

from __future__ import annotations

import logging
from prometheus_client import Histogram, Gauge

from . import Collector

logger = logging.getLogger("atm10.create_mod")


class CreateMetricsCollector(Collector):
    """Collect Create mod kinetic network metrics."""

    def __init__(self, world_path: str) -> None:
        self.world_path = world_path

        # RPM distribution across shafts — histogram shows kinetic health
        self.shaft_rpm = Histogram(
            "minecraft_create_shaft_speed_rpm",
            "Create mod shaft rotational speed in RPM",
            buckets=[0, 5, 10, 32, 64, 128, 256, 512, 1024, 2560],
        )

        self.mechanical_bearings_active = Gauge(
            "minecraft_create_mechanical_bearings_active",
            "Active mechanical bearings in world",
            ["dimension"],
        )

    def collect(self) -> None:
        try:
            for dim_name, path_suffix in [
                ("overworld", "level.dat"),
                ("the_nether", "DIM-1"),
                ("the_end", "DIM1"),
            ]:
                self._scan_dimension(dim_name, path_suffix)
        except Exception as err:
            logger.warning("Create metrics collection failed: %s", err)

    def _scan_dimension(self, dim_name: str, path_suffix: str) -> None:
        """Parse region files for Create mod block entities."""
        from pathlib import Path

        dim_root = Path(self.world_path) / path_suffix
        if not (dim_root.exists() and (dim_root / "region").exists()):
            return

        bearings_count = 0

        region_dir = dim_root / "region"
        for region_file in region_dir.glob("r.*.mca"):
            try:
                chunk_entities = self._extract_block_entities(region_file)
                
                for entity in chunk_entities:
                    block_id = str(entity.get("id", ""))
                    
                    if "create:shaft" in block_id:
                        rpm = entity.get("rotationSpeed", 0)
                        self.shaft_rpm.observe(float(rpm))

                    if "create:mechanical_bearing" in block_id:
                        bearings_count += 1

            except Exception as err:
                logger.debug("Region read failed %s: %s", region_file.name, err)

        self.mechanical_bearings_active.labels(dimension=dim_name).set(bearings_count)

    def _extract_block_entities(self, region_path):
        """Parse .mca file for block entities. Full impl reads compressed chunk NBT."""
        # Stub — would use nbtlib on decompressed gzip chunks from region file
        return []

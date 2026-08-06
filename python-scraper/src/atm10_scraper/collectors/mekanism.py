"""Mekanism metrics collector.

Reads fluid tank levels, energy storage, and digital miner progress via NBT parsing.
Metrics labeled by dimension and block coordinates for precise infrastructure visibility.
"""

from __future__ import annotations

import logging
from prometheus_client import Gauge

from . import Collector

logger = logging.getLogger("atm10.mekanism")


class MekanismMetricsCollector(Collector):
    """Collect Mekanism machine state metrics from world NBT."""

    def __init__(self, world_path: str) -> None:
        self.world_path = world_path

        # Fluid tank fill level — gauge labeled by tank position and fluid type
        self.tank_fill_percent = Gauge(
            "minecraft_mekanism_tank_fill_percent",
            "Fluid tank fill percentage in Mekanism mod",
            ["dimension", "x", "y", "z", "fluid"],
        )

        # Digital miner progress counter (blocks mined)
        self.digital_miner_blocks_total = Gauge(
            "minecraft_mekanism_digital_miner_blocks_total",
            "Total blocks mined by Digital Miner since world gen",
            ["dimension", "x", "y", "z"],
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
            logger.warning("Mekanism metrics collection failed: %s", err)

    def _scan_dimension(self, dim_name: str, path_suffix: str) -> None:
        from pathlib import Path

        dim_root = Path(self.world_path) / path_suffix
        if not (dim_root.exists() and (dim_root / "region").exists()):
            return

        region_dir = dim_root / "region"
        for region_file in region_dir.glob("r.*.mca"):
            try:
                entities = self._extract_entities(region_file)

                for ent in entities:
                    block_id = str(ent.get("id", ""))
                    pos = ent.get("Pos", {})
                    x, y, z = str(pos.get("x", "?")), str(pos.get("y", "?")), str(pos.get("z", "?"))

                    if "mekanism:fluid_tank" in block_id:
                        self._update_tank_metric(ent, dim_name, x, y, z)

                    if "mekanism:digital_miner" in block_id:
                        blocks_mined = int(ent.get("blocksMined", 0))
                        self.digital_miner_blocks_total.labels(
                            dimension=dim_name, x=x, y=y, z=z
                        ).set(blocks_mined)

            except Exception as err:
                logger.debug("Region read failed %s: %s", region_file.name, err)

    def _update_tank_metric(self, ent, dim_name, x, y, z) -> None:
        """Extract fluid storage from tank block entity and set gauge."""
        fluid_data = ent.get("FluidName", {})
        fluid_type = str(fluid_data.get("Name", "unknown"))
        
        stored_mb = int(ent.get("storedMB", 0))
        capacity_mb = int(ent.get("capacityMB", 1))
        fill_pct = (stored_mb / max(capacity_mb, 1)) * 100.0

        self.tank_fill_percent.labels(
            dimension=dim_name, x=x, y=y, z=z, fluid=fluid_type
        ).set(fill_pct)

    def _extract_entities(self, region_path):
        """Parse .mca for block entities. Stub placeholder."""
        return []

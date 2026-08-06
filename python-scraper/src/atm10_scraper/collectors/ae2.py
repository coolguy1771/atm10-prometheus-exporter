"""Applied Energistics 2 metrics collector.

Scans world NBT data for ME drives and cell contents to report storage utilization.
Designed around the AE2 internal NBT structure:
    - ME Drive tag paths include appeng.cell.ME_Drive or ae2:drive block entities
    - Storage tracked as bytes used across all connected cells
"""

from __future__ import annotations

import logging
from prometheus_client import Gauge

from . import Collector

logger = logging.getLogger("atm10.ae2")


class AEMetricsCollector(Collector):
    """Collect AE2 ME network storage metrics from world NBT."""

    def __init__(self, world_path: str) -> None:
        self.world_path = world_path

        self.drive_bytes_used = Gauge(
            "minecraft_ae2_me_drive_bytes_used",
            "Bytes stored in AE2 ME drives by dimension",
            ["dimension"],
        )

        self.drive_count_total = Gauge(
            "minecraft_ae2_me_drives_total",
            "Number of AE2 ME drives loaded",
            ["dimension"],
        )

    def collect(self) -> None:
        """Scan all dimensions for AE2 drive block entities."""
        try:
            self._scan_dimension("overworld", "level.dat")
            self._scan_dimension("the_nether", "DIM-1")
            self._scan_dimension("the_end", "DIM1")
        except Exception as err:
            logger.warning("AE2 collection failed: %s", err)

    def _scan_dimension(self, dim_name: str, path_suffix: str) -> None:
        """Scan one dimension's region files for AE2 drives."""
        import nbtlib
        from pathlib import Path

        dim_root = Path(self.world_path) / path_suffix
        if not dim_root.exists():
            return

        drive_count = 0
        bytes_stored = 0.0

        # Full implementation would open region files (.mca) under region/ subdir
        # and iterate chunk block entities looking for ae2:drive or appeng.cell tags
        # Placeholder below shows structure only:

        region_dir = dim_root / "region"
        if not region_dir.exists():
            return

        for region_file in region_dir.glob("r.*.mca"):
            try:
                chunk_data = self._load_region_chunks(region_file)
                drive_count += self._count_ae2_drives_in_chunk(chunk_data)
                bytes_stored += self._sum_drive_storage(chunk_data)
            except Exception as err:
                logger.debug("Failed to read region %s: %s", region_file.name, err)

        self.drive_bytes_used.labels(dimension=dim_name).set(bytes_stored)
        self.drive_count_total.labels(dimension=dim_name).set(drive_count)

    def _load_region_chunks(self, region_path: Path):
        """Parse Minecraft .mca region file into chunks. Simplified for readability."""
        import struct
        data = region_path.read_bytes()

        offsets = struct.unpack(512 * ">I", data[:2048])
        timestamps = struct.unpack(512 * ">I", data[2048:4096])
        return list(zip(offsets, timestamps))

    def _count_ae2_drives_in_chunk(self, chunk_data) -> int:
        """Count AE2 drives in parsed chunk block entities."""
        count = 0
        # Real implementation parses NBT compound tags per chunk position
        return count

    def _sum_drive_storage(self, chunk_data) -> float:
        """Sum stored bytes across AE2 drive cell inventories."""
        total = 0.0
        # Reads 'bytesUsed' tag from each drive's stored cell stacks
        return total

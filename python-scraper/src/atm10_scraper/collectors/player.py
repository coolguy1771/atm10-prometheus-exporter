"""Player metrics collector from playerdata/*.dat files."""

from __future__ import annotations

import logging
import glob
import nbtlib
from pathlib import Path

from prometheus_client import Gauge

from . import Collector

logger = logging.getLogger("atm10.player")


class PlayerMetricsCollector(Collector):
    """Collect player state metrics from playerdata NBT files."""

    def __init__(self, world_path: str) -> None:
        self.world_path = world_path

        # Player XP level by UUID and name
        self.xp_level = Gauge(
            "minecraft_player_xp_level",
            "Player XP level",
            ["uuid", "name"],
        )

        # Inventory slots occupied
        self.inventory_slots_used = Gauge(
            "minecraft_player_inventory_slots_used",
            "Number of inventory slots in use",
            ["uuid", "name"],
        )

        # Total player count in world (snapshot from last scan)
        self.players_in_world = Gauge(
            "minecraft_players_saved_in_world",
            "Number of player save files found",
        )

    def collect(self) -> None:
        try:
            player_data_dir = Path(self.world_path) / "playerdata"
            if not player_data_dir.exists():
                self.players_in_world.set(0)
                return

            # Reset all previous labels (clear stale players)
            self.xp_level.clear()
            self.inventory_slots_used.clear()

            dat_files = list(player_data_dir.glob("*.dat")) + list(player_data_dir.glob("*.dat_old"))
            count = 0

            for dat_file in dat_files:
                try:
                    uuid_str = dat_file.stem
                    nbt = nbtlib.load(dat_file)
                    
                    # Extract display name if present, else use UUID as fallback
                    name = self._extract_name(nbt, uuid_str)
                    
                    xp_level = int(nbt.get("XpLevel", 0))
                    self.xp_level.labels(uuid=uuid_str, name=name).set(xp_level)

                    # Count occupied inventory slots
                    inventory = nbt.get("Inventory", [])
                    slots_used = sum(1 for item in inventory if "Count" in item and int(item["Count"]) > 0)
                    self.inventory_slots_used.labels(uuid=uuid_str, name=name).set(slots_used)

                    count += 1

                except Exception as err:
                    logger.debug("Failed to parse player file %s: %s", dat_file.name, err)

            self.players_in_world.set(count)

        except Exception as err:
            logger.warning("Player metrics collection failed: %s", err)

    def _extract_name(self, nbt_data, fallback_uuid: str) -> str:
        """Extract player name from NBT Display or CustomName tag."""
        # Check Display/Name compound first (1.20+ style)
        if "Display" in nbt_data and isinstance(nbt_data["Display"], dict):
            if "Name" in nbt_data["Display"]:
                return str(nbt_data["Display"]["Name"])

        # Fallback to CustomName tag
        if "CustomName" in nbt_data:
            return str(nbt_data["CustomName"])

        return fallback_uuid

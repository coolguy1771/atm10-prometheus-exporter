"""Configuration via pydantic-settings with environment variable support."""

from pathlib import Path
from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime configuration for the Prometheus exporter sidecar.

    Loaded from:
      - Environment variables (prefixed ATM10_)
      - .env file in working directory if present
      - CLI overrides via Pydantic's model_config or runtime replacement
    """

    model_config = SettingsConfigDict(
        env_prefix="ATM10_",
        env_file=".env",
        case_sensitive=False,
        extra="ignore",
    )

    # World path — where Minecraft saves world data (read-only mount)
    world_path: Path = Path("/mc-server/world")

    # HTTP server configuration
    bind_host: str = "0.0.0.0"
    bind_port: int = 9345

    # Collection interval in seconds — how often to re-scan NBT data
    scrape_interval_seconds: float = 60.0

    # Feature flags for metric groups
    collect_chunks: bool = True
    collect_players: bool = True
    collect_mod_data: bool = True
    collect_entities: bool = False  # High cardinality; opt-in only


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """Cached singleton accessor for pydantic settings."""
    return Settings()

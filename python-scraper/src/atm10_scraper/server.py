"""FastAPI HTTP server exposing Prometheus /metrics with periodic collection.

Architecture:
  - FastAPI application serves /metrics (Prometheus exposition format) + /health
  - Background task loop runs all collectors on a configurable schedule via asyncio.to_thread
"""

from __future__ import annotations

import asyncio
import logging
from typing import TYPE_CHECKING

from fastapi import FastAPI
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest, REGISTRY

if TYPE_CHECKING:
    from .config import Settings

logger = logging.getLogger("atm10.server")


def create_app(settings: Settings) -> FastAPI:
    """Factory that builds the FastAPI application wired to config + collectors."""

    app = FastAPI(
        title="ATM10 Prometheus Exporter",
        version="1.0.0-alpha",
        docs_url="/docs",
        redoc_url=None,  # disable extra endpoint; /metrics is primary interface
    )

    # Build collector list based on settings flags
    collectors = _build_collectors(settings)

    @app.get(
        "/metrics",
        response_headers={"Content-Type": CONTENT_TYPE_LATEST},
    )
    async def metrics():
        """Prometheus exposition format endpoint for scraping."""
        return generate_latest(REGISTRY)

    @app.get("/health")
    async def health():
        """Liveness probe for orchestrators/load balancers."""
        return {
            "status": "ok",
            "world_path": str(settings.world_path),
            "collectors_active": len(collectors),
        }

    @app.on_event("startup")
    async def on_startup():
        """Spawn background collection loop after server starts."""
        app.state.collection_task = asyncio.create_task(
            _collection_loop(collectors, settings.scrape_interval_seconds)
        )

    @app.on_event("shutdown")
    async def on_shutdown():
        """Gracefully cancel collection task on shutdown."""
        if hasattr(app.state, "collection_task"):
            app.state.collection_task.cancel()
            try:
                await app.state.collection_task
            except asyncio.CancelledError:
                pass

    return app


def _build_collectors(settings: Settings):
    """Instantiate collectors respecting config feature flags."""
    from .collectors.ae2 import AEMetricsCollector
    from .collectors.create_mod import CreateMetricsCollector
    from .collectors.mekanism import MekanismMetricsCollector
    from .collectors.player import PlayerMetricsCollector

    result = []

    if settings.collect_players:
        result.append(PlayerMetricsCollector(world_path=str(settings.world_path)))

    if settings.collect_chunks and settings.collect_mod_data:
        result.append(AEMetricsCollector(world_path=str(settings.world_path)))
        result.append(CreateMetricsCollector(world_path=str(settings.world_path)))
        result.append(MekanismMetricsCollector(world_path=str(settings.world_path)))

    logger.info("Initialized %d metric collectors", len(result))
    return result


async def _collection_loop(collectors, interval_seconds: float):
    """Periodically run all registered collectors in background."""
    while True:
        try:
            await asyncio.sleep(interval_seconds)
            await _run_collectors(collectors)
        except asyncio.CancelledError:
            logger.info("Collection loop cancelled; shutting down gracefully")
            break
        except Exception as err:
            logger.error("Unexpected error in collection loop: %s", err, exc_info=True)


async def _run_collectors(collectors):
    """Execute all collectors concurrently via thread pool."""
    if not collectors:
        return

    tasks = [asyncio.to_thread(c.collect) for c in collectors]
    await asyncio.gather(*tasks, return_exceptions=True)

"""Entry point for ATM10 world scraper.

Usage (after install via uv):
    atm10-scraper run --world /path/to/world --port 9345

Or with uv without installing:
    uv run python -m atm10_scraper.main run
"""

from __future__ import annotations

import asyncio
import logging
import signal
import sys

from uvicorn.config import Config
from uvicorn.server import Server
from .config import get_settings, Settings


def configure_logging(verbose: bool = False):
    """Set up structured logging once at startup."""
    level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(
        level=level,
        format="%(asctime)s [%(levelname)-8s] %(name)s - %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )


def main():
    """Main entry point called via pyproject.scripts.atm10-scraper."""
    import argparse

    parser = argparse.ArgumentParser(
        description="ATM10 Minecraft world data Prometheus exporter"
    )
    parser.add_argument("command", choices=["run"], help="Command to execute")
    parser.add_argument("--world", type=str, default=None, help="World directory path")
    parser.add_argument("--host", type=str, default=None, help="Bind host")
    parser.add_argument("--port", type=int, default=None, help="Bind port")
    parser.add_argument(
        "--interval", type=float, default=None, help="Scrape interval in seconds"
    )
    parser.add_argument("-v", "--verbose", action="store_true", help="Debug logging")

    args = parser.parse_args()

    if args.command != "run":
        parser.print_help()
        sys.exit(1)

    configure_logging(args.verbose)

    # Start with environment-loaded settings, then apply CLI overrides
    settings = get_settings().model_copy()

    if args.world is not None:
        settings.world_path = args.world
    if args.host is not None:
        settings.bind_host = args.host
    if args.port is not None:
        settings.bind_port = args.port
    if args.interval is not None:
        settings.scrape_interval_seconds = args.interval

    logging.info(
        "Starting ATM10 Prometheus exporter on %s:%d (world=%s, interval=%.1fs)",
        settings.bind_host,
        settings.bind_port,
        settings.world_path,
        settings.scrape_interval_seconds,
    )

    # Build FastAPI app
    from .server import create_app

    app = create_app(settings)

    # Configure uvicorn
    config = Config(
        app=app,
        host=settings.bind_host,
        port=settings.bind_port,
        log_level="info",
        access_log=False,  # reduce noise; /metrics is scraped programmatically
    )
    server = Server(config)

    # Handle graceful shutdown via signals
    loop = None

    def _signal_handler(sig):
        logging.info("Received signal %s; shutting down...", sig.__name__)
        server.should_exit = True

    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop = asyncio.get_running_loop()
            loop.add_signal_handler(sig, lambda s=sig: _signal_handler(s))
        except RuntimeError:
            # No running loop yet; handle below
            pass

    try:
        server.run()
    except KeyboardInterrupt:
        logging.info("Interrupted by user")
        sys.exit(0)


if __name__ == "__main__":
    main()

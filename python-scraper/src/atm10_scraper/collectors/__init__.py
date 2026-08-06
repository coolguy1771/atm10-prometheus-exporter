"""Metric collectors for ATM10 world data."""

from __future__ import annotations

import abc


class Collector(abc.ABC):
    """Abstract base for Prometheus metric collectors.

    Collectors read world state and register/update Prometheus metrics during collect().
    Design principle: fail silently — any single collector failure must not kill the exporter.
    """

    @abc.abstractmethod
    def collect(self) -> None:
        """Execute collection logic and update registered metrics."""

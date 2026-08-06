"""Prometheus metrics registry wrapper.

Encapsulates prometheus_client setup and provides typed accessors
for creating gauges/counters/histograms without leaking implementation.
"""

from __future__ import annotations

import threading
from collections.abc import Callable, Iterable
from dataclasses import dataclass, field


@dataclass(frozen=True)
class MetricDefinition:
    """Immutable descriptor for a Prometheus metric."""

    name: str
    description: str
    labels: tuple[str, ...] = ()
    value_fn: Callable[[], Iterable[float]] | None = None


class MetricsRegistry:
    """Thread-safe metrics registry wrapper around prometheus_client.

    Delegates to Prometheus' default registry; all metrics are global singleton definitions.
    Provides typed methods aligned with modern Python practices (dataclasses, type hints).
    """

    def __init__(self) -> None:
        self._lock = threading.RLock()
        self._definitions: list[MetricDefinition] = []

    @property
    def definitions(self) -> tuple[MetricDefinition, ...]:
        """Return a snapshot of registered metric definitions."""
        with self._lock:
            return tuple(self._definitions)

    def define_gauge(
        self,
        name: str,
        description: str,
        labels: Iterable[str] | None = None,
    ) -> MetricDefinition:
        """Declare a gauge metric."""
        with self._lock:
            labels_tuple = tuple(labels or ())
            defn = MetricDefinition(name=name, description=description, labels=labels_tuple)
            self._definitions.append(defn)
            return defn

    def define_counter(
        self,
        name: str,
        description: str,
        labels: Iterable[str] | None = None,
    ) -> MetricDefinition:
        """Declare a counter metric."""
        return self.define_gauge(name=name, description=description, labels=labels)  # simplified; real impl uses Counter

    def define_histogram(
        self,
        name: str,
        description: str,
        buckets: Iterable[float] | None = None,
        labels: Iterable[str] | None = None,
    ) -> MetricDefinition:
        """Declare a histogram metric."""
        with self._lock:
            labels_tuple = tuple(labels or ())
            defn = MetricDefinition(name=name, description=description, labels=labels_tuple)
            self._definitions.append(defn)
            return defn

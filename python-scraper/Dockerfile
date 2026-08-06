# FastAPI + uv-based ATM10 Prometheus exporter sidecar
# Multi-stage build: lock+build in builder, run in minimal runtime image

FROM python:3.12-slim-bookworm AS base

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=off \
    UV_PYTHON_DOWNLOADS="never"

WORKDIR /app

# Install uv for fast, lockable dependency management
RUN pip install --no-cache-dir uv==0.6.x

COPY pyproject.toml uv.lock* ./ || true

# Generate lock file if missing, then install deps into virtual environment
RUN if [ ! -f uv.lock ]; then uv lock; fi && \
    uv sync --frozen --no-dev || uv sync --no-frozen --no-dev

# Copy application code
COPY src /app/src

# Install project itself (creates atm10-scraper console script via pyproject.scripts)
RUN uv pip install --system -e .

# Non-root user for security best practices
RUN adduser --disabled-password --gecos "" --uid 1001 scraper-user && \
    chown -R scraper-user:scraper-user /app
USER scraper-user

EXPOSE 9345/tcp

ENTRYPOINT ["atm10-scraper"]
CMD ["run", "--help"]

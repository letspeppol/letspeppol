# Monitoring Stack

This folder spins up the observability toolchain (MinIO, Mimir, Loki, Grafana, Alloy).

## Prerequisites

- Docker / Docker Compose v2
- The application services (e.g. `docker/local`, `docker/blue`, `docker/green`) must share the `observability` network
- `kyc` exposes `/actuator/prometheus` on port `8084`

## Quick start

```sh
cd docker/monitoring
# Create shared network once (no-op if it already exists)
docker network create observability || true

# Start storage + monitoring plane
docker compose up -d minio createbuckets mimir loki alloy grafana
```

Grafana: http://localhost:3000 (default `admin` / `admin`).

## What runs here

| Service  | Purpose                                  |
|----------|-------------------------------------------|
| MinIO    | S3-compatible storage backend for Mimir   |
| Mimir    | Long-term Prometheus-compatible metrics   |
| Loki     | Central log store                         |
| Alloy    | Agent scraping metrics + receiving logs   |
| Grafana  | Dashboards, log exploration, alerting     |

Alloy scrapes `kyc:8084/actuator/prometheus` and remote-writes into Mimir with tenant `1`. Logs pushed to `http://alloy:3200/loki/api/v1/push` are forwarded into Loki.

## Dashboards & datasources

Grafana is pre-provisioned with:

- Datasources: Loki + Mimir (`docker/monitoring/grafana/provisioning/datasources`)
- Dashboards: `KYC Service Overview` (`docker/monitoring/grafana/dashboards`)

Files under `grafana/dashboards` are automatically loaded into the *KYC* folder.

## Validating data flow

1. Start monitoring stack (above)
2. Start a `kyc` container that joins `observability`
3. Hit any KYC endpoint to generate metrics/logs
4. Grafana → Explore → Loki (`{source="kyc"}`) to see logs
5. Grafana → Dashboards → *KYC* → *KYC Service Overview* to see Micrometer counters

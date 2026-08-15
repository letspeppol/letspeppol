# Monitoring Stack

This folder spins up the observability toolchain (MinIO, Mimir, Loki, Grafana, Alloy).

## Prerequisites

- Docker / Docker Compose v2
- Start this stack before the application stacks so it can create the shared `observability` network
- The blue and green application services join that network with deployment-specific aliases
- `kyc` exposes `/actuator/prometheus` on port `8084`

## Quick start

```sh
cd docker/monitoring
# Start storage + monitoring plane
docker compose --env-file .env_live up -d minio createbuckets mimir loki alloy grafana
```

Grafana is available through Traefik at `https://<GRAFANA_DOMAIN>:8443`.

## What runs here

| Service  | Purpose                                  |
|----------|-------------------------------------------|
| MinIO    | S3-compatible storage backend for Mimir   |
| Mimir    | Long-term Prometheus-compatible metrics   |
| Loki     | Central log store                         |
| Alloy    | Agent scraping metrics + receiving logs   |
| Grafana  | Dashboards, log exploration, alerting     |

Alloy scrapes the blue and green application services through deployment-specific aliases and remote-writes into Mimir with tenant `1`. Logs pushed to `http://alloy:3200/loki/api/v1/push` are forwarded into Loki.

## Dashboards & datasources

Grafana is pre-provisioned with:

- Datasources: Loki + Mimir (`docker/monitoring/grafana/provisioning/datasources`)
- Dashboards: `KYC Service Overview` (`docker/monitoring/grafana/dashboards`)

Files under `grafana/dashboards` are automatically loaded into the *KYC* folder.

## Validating data flow

1. Start monitoring stack (above)
2. Start the blue or green application stack
3. Hit any KYC endpoint to generate metrics/logs
4. Grafana → Explore → Loki (`{source="kyc"}`) to see logs
5. Grafana → Dashboards → *KYC* → *KYC Service Overview* to see Micrometer counters

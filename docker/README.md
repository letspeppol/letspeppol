# Docker deployment

Shared networks are created by the infrastructure stack that owns them:

- Traefik creates `web`.
- Database creates the internal `database` network.
- Monitoring creates the internal `observability` network.

Start the stacks in dependency order after a fresh Docker installation or state reset:

```sh
docker compose --env-file docker/traefik/.env_live -f docker/traefik/docker-compose.yml up -d
docker compose --env-file docker/database/.env_live -f docker/database/docker-compose.yml up -d
docker compose --env-file docker/monitoring/.env_live -f docker/monitoring/docker-compose.yml up -d
docker compose --env-file docker/green/.env_live -f docker/green/docker-compose.yml up -d
```

Port `8443` binds to localhost by default. Set `INTERNAL_BIND_ADDRESS` in the Traefik environment to the host's Tailscale address when Grafana and Adminer should be reachable from the tailnet.

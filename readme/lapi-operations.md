# LAPI operations

Operator reference for local maintenance endpoints. This document is separate from the public API
flow guide and generated OpenAPI documentation. Its visibility follows the repository's visibility.

## Access

Use the deployment's local/internal entrypoint, normally `http://localhost:8443`.
The paths below include the service prefix used by that entrypoint. Public blue/green routers
exclude LAPI paths; these operations depend on that network restriction for operator access.

## Operations

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/proxy/lapi/monitor` | Basic proxy health probe; returns `ok`. |
| GET | `/proxy/lapi/monitor/{amount}` | Adjust the monitored balance; returns the resulting balance. This request changes state. |
| POST | `/kyc/lapi/company/{peppolId}/move` | Move an active registration to another access point. |
| GET | `/kyc/lapi/kbo` | Basic KBO endpoint health probe; returns `ok`. |
| POST | `/kyc/lapi/kbo/sync` | Run the initial KBO synchronization. Existing KYC CSRF protection applies. |

## Moving a registration

```sh
curl --fail-with-body -X POST \
  'http://localhost:8443/kyc/lapi/company/0208:0123456789/move?accessPoint=RECOMMAND'
```

The optional `accessPoint` selects an available provider: `SCRADA`, `RECOMMAND`, or `E_INVOICE`.
Omit it to use `proxy.access-point`. Selecting the current provider succeeds without moving it.
Unknown or unavailable targets, including `NONE` and `LOOPBACK`, are rejected.

No request body is needed. KYC loads its company details and signed ADMIN contract, then sends
them to `POST /proxy/sapi/registry/{peppolId}/move` using its existing service credentials.
The proxy validates both providers and handles the migration and recovery. KYC forwards the
result and updates its registration state. Missing data or a missing contract stops migration
before deregistration. Suspended companies cannot be migrated.

| Status | Meaning |
| --- | --- |
| 200 | The registration moved successfully, or already uses the selected provider. |
| 400 | Invalid target or registration payload. |
| 404 | The Peppol ID has no stored registration. |
| 409 | The current registration has no active, available provider. |
| 424 | The move failed and restoring the original provider was attempted. |

A 424 response includes the resulting `accessPoint` and `peppolActive` state. If recovery succeeded,
the original provider is active. If recovery also failed, `accessPoint` is `NONE` and `peppolActive`
is false. Other provider or storage failures may return an error response; inspect operator logs.

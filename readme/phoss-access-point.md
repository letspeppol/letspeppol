# PHOSS access-point integration

Implemented on `feature/phoss-access-point`, based on `contributors` at `232d73fe`.
The outbound local-lab milestone has passed real AS4 verification in both directions.

## Architecture and scope

```text
Browser / Corner 1
  -> existing authenticated LetsPeppol App backend
  -> existing LetsPeppol Proxy document APIs, queue and scheduler
  -> PhossService implements AccessPointServiceInterface
       -> PHOSS AP: submit SBD, query outbound status
       -> PHOSS SMP: read participant/service metadata

Verified local transport:
Proxy -> PHOSS AP -> signed/encrypted AS4 TEST loopback -> PHOSS AP receiver
      -> existing PHOSS HTTP test backend
```

The live smoke tests call the **real Proxy backend** with signed test JWTs. They
exercise its security chain, UBL validation, persistence, scheduler, PHOSS adapter,
SMP lookup and delivery details. They do not launch the browser, App backend or KYC
onboarding. The standalone JWT fixture is not KYC and must never be deployed.
The normal App-to-Proxy document flow is unchanged.

The lab still bypasses **outbound** SMP discovery in its guarded TEST loopback mode;
**receiving-side** SMP checks remain enabled. These results do not demonstrate public
Peppol discovery, production PKI, another AP, or arbitrary-recipient delivery.
No certificate-validation exception was added to LetsPeppol.

### Implemented

- `AccessPoint.PHOSS` and migration `V1.9__phoss_access_point.sql`.
- Optional `PhossConfig` and separate authenticated AP / unauthenticated SMP clients.
- Namespace-correct SBDH wrapping of **Peppol BIS Billing Invoice** UBL. Envelope
  parties must match UBL endpoints; unsupported/self-billing/credit-note documents
  fail explicitly. XML DTDs and external entities are disabled.
- Direct `POST /api/outbound/submit-sbd`, authenticated status polling, and sanitized
  receipt diagnostics through the existing delivery-details API.
- Read-only SMP service-metadata lookup through the service-authorized
  `GET /sapi/registry/receiving-availability` route when PHOSS is the default provider.
  A 404 means absent metadata; other failures are errors, not proof of absence.
  This lookup returns a descriptive label, not an internal Docker URL. It is not
  cryptographic signature verification; AP metadata validation remains authoritative.
- Local send-only registration with C1 country stored in the registry. This does not
  create a public Peppol registration, alter SMP, or enable receiving. Existing
  companies are not automatically moved when the default provider changes.

### Not implemented yet

- Automatic SMP administration by `PhossService`: registration ownership, document/
  process provisioning, receiving settings, SML coordination, safe deletion.
  `dev/phoss/register-receiver.sh` is an explicit **local lab operator tool**, not the
  production registration implementation.
- Incoming invoices in LetsPeppol's inbox. The AP still forwards to its original test
  backend. Receiving-setting writes requesting `true` are rejected explicitly.
- Credit notes/self-billing transport and production readiness.

## Credentials and delivery safety

Settings (PHOSS is disabled by default):

| Environment variable | Purpose |
| --- | --- |
| `PHOSS_ENABLED=true` | Opt into the adapter |
| `PEPPOL_ACCESS_POINT=PHOSS` | Select PHOSS for new local registrations |
| `PHOSS_AP_URL` | Server-side AP base URL |
| `PHOSS_SMP_URL` | Server-side SMP base URL |
| `PHOSS_TOKEN_FILE` | File containing only the AP token, optionally newline-terminated |

Incomplete enabled configuration fails startup. The AP token must not go into a
browser/Vite variable, image, Git, log, or SMP request. Clients do not follow HTTP
redirects. Use HTTPS outside this isolated local network.

`proxy.phoss_submission` commits the SBDH identity and an atomic attempt marker in
**independent transactions before HTTP**. Concurrent callers and scheduler rollbacks
cannot cause a second POST under a new identity. A fingerprint prevents changing
content under an existing document UUID. The table stores no extra invoice payload,
including for no-archive documents. Keep these identity tombstones when managing
retention; removing them removes the replay protection for those document UUIDs.

A network timeout, unexpected response or process crash is ambiguous: Proxy retains
the identity, polls AP status and **does not blindly resubmit**. If AP status remains
404, an operator must reconcile whether the AP accepted the submission. A crash
between committing the attempt and transmitting can therefore require manual
intervention. This deliberately favors duplicate prevention over automatic recovery.
Do not reset the marker or submit a replacement invoice until the outcome is known.

PHOSS owns its AS4 retries. `pending`, `sending`, `recovering`, retryable `failed`,
unknown states and temporarily unavailable status remain unfinished; `sent` succeeds;
`rejected` and `permanently_failed` fail. HTTP 422 submission reports may describe a
persisted transaction awaiting AP retry and are not treated as immediate terminal
failure. HTTP 400/401/403/404 submission rejection is terminal and sanitized.

Only selected report fields are retained server-side. Raw receipt XML is not exposed
through document details; the receipt message ID and timestamp can be exposed there.
If the initial submission response is lost, or PHOSS succeeds on a later retry,
status can still complete even if no successful receipt was captured by Proxy. For
those cases inspect the AP's own transport evidence; a missing cached receipt is not
an AS4 rejection.

## Networking

The contributors' `devenv up` launches host processes bound to loopback. They cannot
resolve Docker names `ap`, `smp` and `testbackend`. Do not put these names in browser
configuration or publish AP/SMP ports as a workaround.

Two opt-in setups are supplied:

1. **Existing full Docker local stack:** add
   `docker/local/docker-compose.phoss.yml` to the usual Compose invocation. Only
   Proxy joins the existing external `phoss-local` network; it retains its normal
   network for DB, KYC and App. The base stack still requires its documented TLS,
   observability network and environment configuration.
2. **Standalone transport verification:** `dev/phoss/compose.yml` starts a separate
   Proxy database, an internal JWT fixture and the real Proxy. Only Proxy also joins
   `phoss-local`. It publishes **no host ports** and does not replace any PHOSS
   container. State lives in `.devenv/state/phoss-test/` and a separate Docker volume.

Both retain `phoss-local` as internal. Attaching a dual-network backend expands the
lab trust boundary, even without publishing ports; this attachment was explicitly
approved for this session. No host networking, forwarding changes or certificate
rotation was used.

## RUN — Reproduce the standalone test

Use the repository's devenv shell (Java 25, Gradle wrapper, Perl, OpenSSL). Docker
commands below use `sg docker -c` for this machine's stale supplementary groups.
Leave existing PHOSS registrations and evidence alone.

```bash
devenv shell
./proxy/gradlew -p proxy bootJar phossLabLibraries
umask 077
mkdir -p .devenv/state/phoss-test
export PHOSS_TOKEN_FILE="$PWD/.devenv/state/phoss-test/ap-token"
export PHOSS_SAMPLE_SBD="/home/B/Projects/phax/phoss/evidence/submitted-sbd.xml"
perl -ne 'print "$1\n" if /^phase4.api.requiredtoken\s*=\s*(.*?)\s*$/' /home/B/Projects/phax/phoss/ap.properties > "$PHOSS_TOKEN_FILE"
chmod 600 "$PHOSS_TOKEN_FILE"
sg docker -c 'docker compose -f dev/phoss/compose.yml up -d'
sg docker -c 'docker logs --tail 20 letspeppol-phoss-test-proxy-1'
```

**CHECK:** Proxy has started; the original AP is healthy; both networks are internal;
none of these containers has published host ports. Neither the AP token nor a JWT
should be printed. `LabSupport.java` mints short-lived fixture tokens only on the
internal test network; real JWT signature, audience and issuer validation stay on.

First test the existing receiver, with a distinct sender to avoid Proxy's internal
`LOOPBACK` shortcut. Each command needs a new evidence directory name:

```bash
export RUN_ID=$(date -u +%Y%m%dT%H%M%SZ)
sg docker -c "docker exec letspeppol-phoss-test-proxy-1 java --class-path '/support/libs/*' /support/LabSupport.java smoke /state/evidence/existing-$RUN_ID 9915:phoss-ap-test-sender 9915:localhost-8080"
```

The helper checks the receiver's SMP metadata **before sending**, registers the
sender only in the standalone Proxy's local registry, verifies that anonymous access
is rejected, and submits through the authenticated document API. It requires `sent`,
a correlated AS4 Receipt, and matching forwarded SBDH/UBL. It refuses to overwrite
an existing evidence directory. Reruns generate fresh invoice, Proxy and SBDH IDs.

For the two new receivers created in this session:

```bash
sg docker -c "docker exec letspeppol-phoss-test-proxy-1 java --class-path '/support/libs/*' /support/LabSupport.java smoke /state/evidence/a-b-$RUN_ID 9915:letspeppol-lab-a 9915:letspeppol-lab-b"
sg docker -c "docker exec letspeppol-phoss-test-proxy-1 java --class-path '/support/libs/*' /support/LabSupport.java smoke /state/evidence/b-a-$RUN_ID 9915:letspeppol-lab-b 9915:letspeppol-lab-a"
```

### DO — Add a new local receiver, only when needed

Supply `PHOSS_AP_CERT` pointing to the **existing public** AP PEM and
`SMP_CREDENTIALS_FILE` pointing to a private curl netrc file (`machine smp login ...
password ...`). Do not commit that file. The helper accepts only local `9915` IDs,
requires the network to be internal, refuses an existing participant, disables SML
creation, advertises the existing `http://ap:8080/as4`, and saves signed metadata.
It does not change the AP certificate or the existing receiver registration.

```bash
export PHOSS_AP_CERT=/home/B/Projects/phax/phoss/ap.pem
export SMP_CREDENTIALS_FILE="$PWD/.devenv/state/phoss-test/smp.netrc"
sg docker -c 'export PATH="$PWD/.devenv/profile/bin:$PATH"; bash dev/phoss/register-receiver.sh 9915:choose-a-new-local-id'
```

If participant creation succeeds but metadata creation fails, stop and inspect the
saved evidence. Do not bypass the existing-participant guard to rerun blindly.

## CHECK — Recorded results (2026-11-27)

All **177 Proxy tests passed**, including PostgreSQL-backed independent-commit and
concurrent-claim tests. An isolated host PostgreSQL test instance was used; it did
not use or modify the PHOSS database, and was stopped after testing (data retained).
The frontend production build, `git diff --check` and the registration helper's Bash
syntax check passed. The token is mounted read-only; the backend cannot access the
host-only SMP credential file. A scan found no AP token in any changed/new non-ignored
file.

First live run used the original `9915:localhost-8080` receiver. Final bidirectional
runs additionally exercised the Proxy's direct SMP lookup endpoint:

| Direction | Proxy document | SBDH instance |
| --- | --- | --- |
| Original test sender → original receiver | `1bb82a1c-790f-46cd-9ffb-e7577facb5b9` | `1dfb17fc-7a8f-437d-bdde-31518c9e92ab` |
| `9915:letspeppol-lab-a` → `9915:letspeppol-lab-b` | `a8b34979-9c86-49a9-8e32-84b6ed72bcbc` | `bd75ac22-2f16-4760-bb1e-f1669d676031` |
| `9915:letspeppol-lab-b` → `9915:letspeppol-lab-a` | `aa505a30-ee29-4eb7-90a5-3ece2387f437` | `965b21ce-115a-4a14-ac24-3cee123fac19` |

Evidence is local and git-ignored:

- `.devenv/state/phoss-test/evidence/first-registered-receiver/`
- `.devenv/state/phoss-test/evidence/final-a-to-b/`
- `.devenv/state/phoss-test/evidence/final-b-to-a/`
- `.devenv/state/phoss-test/registrations/`

Each successful proof includes submitted UBL, Proxy registration/status/details,
AP status, submission report, Receipt, backend list, forwarded SBD and `result.txt`.
The final runs also include raw SMP metadata and Proxy lookup results.

Seven local documents were created in total and all completed. Two staging sends
ran before the registration helper's missing OpenSSL PATH was corrected; their
initial AS4 failures were retained as retryable by PHOSS and later succeeded through
PHOSS-managed retries without another Proxy POST. Their incomplete early smoke
output remains preserved. Separate clean final runs above prove Receipt/content
matching; they are not confused with those recovery attempts.

The original AP/SMP/backend/DB containers, certificate material, forwarding endpoint,
existing receiver and historical evidence were preserved. PHOSS source edits remain
uncommitted and untouched. The standalone test stack remains available. Stopping it
without deleting volumes preserves its database; do not use `down -v`, clear the
backend, remove registrations or rotate keys as cleanup.

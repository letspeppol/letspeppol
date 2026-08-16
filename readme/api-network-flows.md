# API network flows

## Purpose

This guide groups the implemented HTTP APIs by network responsibility. Each linked page contains a
focused Mermaid sequence diagram, explains its authentication and trust boundaries, and points to
an executable test that proves the flow.

Endpoint-level request and response schemas remain in each service's generated OpenAPI document.
Paths in these pages include the external `/kyc`, `/app`, or `/proxy` prefix added by Nginx. Calls
made directly on the container network omit that prefix.

For the detailed KYC onboarding scenarios, see [KYC and registration flows](./kyc.md).

## API boundaries

| Family | Exposure | Authentication |
| --- | --- | --- |
| `/api/**` | Public | None, except prepared contract view/finalize require the short-lived `X-Signing-Session` capability created by successful eID preparation |
| `/auth/browser/**` | Browser authentication surface | Anonymous/session cookie depending on login stage; CSRF on mutations |
| `/auth/oauth2/**` | OAuth2 protocol surface | Endpoint-specific browser session, PKCE, or OAuth2 client authentication |
| `/auth/oidc/**` | OpenID Connect protocol surface | Bearer access token |
| `/.well-known/**` | OAuth2/OIDC discovery | Public; location is derived from the configured issuer |
| `/sapi/**` | Protected API | KYC JWT; endpoint determines user or `service` scope |

All KYC JWT consumers validate the signature through `/kyc/auth/oauth2/jwks`, expiry, audience
`letspeppol-api`, and the configured issuer. The OAuth password and refresh-token grants are not
enabled for the SPA. Traefik may route and rate-limit each `/kyc/auth/*` subgroup independently,
while Spring Security retains the endpoint-specific authentication decisions.

## Flow documentation

1. [Browser authentication, OAuth2, and account security](./api-authentication-and-account-security.md)
2. [Registration, identity signing, and company activation](./api-registration-and-company-activation.md)
3. [Public discovery and aggregate statistics](./api-public-discovery.md)
4. [Application profile and master data](./api-application-profile-and-master-data.md)
5. [Business-document lifecycle and Peppol transport](./api-document-lifecycle.md)
6. [Service links, registry control, and sponsor invoices](./api-service-links-and-registry.md)

## How to read the flow pages

Each page groups APIs that share a caller, trust boundary, or business lifecycle. A grouped arrow can
represent closely related item, search, or action routes; the text below the diagram identifies those
variants. The `Executable proof` link above every diagram points to the integration or focused test
that validates the depicted behavior.

The KYC, App, and Proxy `OpenApiDocumentationTest` classes enforce that every documented controller
operation has a summary and description, every non-hidden implemented route occurs on one of the
pages linked above, and every linked page contains both a Mermaid diagram and an executable-proof
reference.

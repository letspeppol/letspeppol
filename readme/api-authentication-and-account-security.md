# Browser authentication, OAuth2, and account security

This flow covers browser session discovery, password/TOTP and passkey login, OAuth2 Authorization
Code with PKCE, JWT validation by downstream APIs, account ownership selection, and security-factor
management.

Executable proof: [`RegistrationTest`](../kyc/src/test/java/org/letspeppol/kyc/controller/RegistrationTest.java), method `oauth2AuthorizationCodeWithPkce`, plus the focused TOTP, passkey, password-reset, and OpenAPI documentation tests referenced in the diagram.

```mermaid
sequenceDiagram
    actor User
    participant UI as Frontend
    participant KYC
    participant API as App or Proxy

    Note over User,API: Proof: RegistrationTest.oauth2AuthorizationCodeWithPkce<br/>CompanyUserTest.removedUserKeepsTheAccountButLosesTheCompany<br/>TotpAuthenticationSuccessHandlerTest<br/>PasskeyControllerBrowserAuthTest<br/>PasswordResetServiceTests<br/>KYC OpenApiDocumentationTest
    UI->>KYC: GET /kyc/auth/browser/session
    KYC-->>UI: session status + CSRF token
    alt Password, then optional TOTP
        UI->>KYC: POST /kyc/auth/browser/login (form + CSRF)
        opt TOTP or recovery code required
            UI->>KYC: POST /kyc/auth/browser/totp
        end
    else Passkey
        UI->>KYC: POST /kyc/auth/browser/passkeys/authenticate/options
        UI->>KYC: POST /kyc/auth/browser/passkeys/authenticate/verify
    end
    UI->>KYC: GET /kyc/auth/oauth2/authorize (S256 + peppol_id + account_type)
    alt Account has an active ownership
        KYC->>KYC: Validate and persist exact ownership selection
    else Account is linked to no active company
        KYC->>KYC: Persist an authorization without a company
    end
    KYC-->>UI: redirect_uri?code=...&state=...
    UI->>KYC: POST /kyc/auth/oauth2/token (code verifier)
    KYC-->>UI: access_token + id_token, no refresh token
    UI->>API: /sapi/** with Bearer access_token
    API->>KYC: GET /kyc/auth/oauth2/jwks (cached key discovery)

    opt Manage authenticated account security
        UI->>KYC: GET /kyc/sapi/account
        UI->>KYC: GET /kyc/sapi/account/ownerships
        UI->>KYC: POST /kyc/sapi/account/ownership
        UI->>KYC: POST /kyc/sapi/password/change
        UI->>KYC: POST /kyc/sapi/totp/setup
        UI->>KYC: POST /kyc/sapi/totp/enable
        UI->>KYC: POST /kyc/sapi/totp/disable
        UI->>KYC: GET /kyc/sapi/totp/status
        UI->>KYC: POST /kyc/sapi/passkeys/register/options
        UI->>KYC: POST /kyc/sapi/passkeys/register/verify
        UI->>KYC: GET /kyc/sapi/passkeys
        UI->>KYC: PUT /kyc/sapi/passkeys/{id}/name
        UI->>KYC: DELETE /kyc/sapi/passkeys/{id}
    end
    opt Recover forgotten password without a session
        UI->>KYC: POST /kyc/api/password/forgot
        UI->>KYC: POST /kyc/api/password/reset
    end
    opt End the OpenID Connect browser session
        UI->>KYC: GET /kyc/auth/browser/logout (id_token_hint)
    end
```

`POST /kyc/sapi/account/ownership` records `lastUsed` only as a future default. The new company/role
claims come from the explicit ownership selector on the PKCE authorization request. KYC stores that
selector with the authorization code and revalidates it at exchange, so another tab changing
`lastUsed` cannot alter the resulting token; see [Swap](./swap.md).

An account whose companies were all removed or suspended still signs in. Its token carries `uid` and
`permissionMask: 0`, but no `peppolId` or `accountType`. KYC accepts that token only on the account
endpoints in the diagram (`/sapi/account/**`, `/sapi/password/change`, `/sapi/totp/**`,
`/sapi/passkeys/**`); App and the proxy grant it nothing. The UI then offers the Account page and Add
company only. A selector naming a role the account does not hold on a company it still belongs to keeps
failing with `ownership_unavailable`.

OAuth2 and OpenID Connect discovery remain at their standardized, issuer-derived
`/.well-known/**` locations rather than moving under `/auth/**`; the public KYC reverse proxy must
continue routing those locations to the authorization server. Their metadata advertises the grouped
authorization, token, JWKS, UserInfo, and logout endpoints.

Return to the [API network flow guide](./api-network-flows.md).

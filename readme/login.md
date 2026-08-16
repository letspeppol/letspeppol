# Login

Login runs as an OAuth2 Authorization Code flow with PKCE against KYC (the authorization server).
The login interface is an Aurelia component in `app/ui`, while credential validation and the browser
session remain owned by KYC. The UI first establishes a KYC session with a CSRF-protected password,
TOTP/recovery-code, or passkey request. It then starts the OAuth flow and exchanges the authorization
code for an access token. Passwords and MFA codes are never exchanged directly for a JWT, and the
OAuth password grant is not enabled.

Protected application URLs start with the active Peppol ID, for example
`/0208:0123456789/invoices/17`. The UI sends that Peppol ID and the tab's remembered account type as
`peppol_id` and `account_type` on the authorization request. KYC validates the ownership, stores the
exact selection with the authorization code, and revalidates it during token exchange. `lastUsed` is
only the default when login or a context-free `/dashboard` request supplies no selection.

Executable proof: [`RegistrationTest`](../kyc/src/test/java/org/letspeppol/kyc/controller/RegistrationTest.java), method `oauth2AuthorizationCodeWithPkce`. TOTP and passkey branches are covered by [`TotpAuthenticationSuccessHandlerTest`](../kyc/src/test/java/org/letspeppol/kyc/config/TotpAuthenticationSuccessHandlerTest.java) and [`PasskeyControllerBrowserAuthTest`](../kyc/src/test/java/org/letspeppol/kyc/controller/PasskeyControllerBrowserAuthTest.java).

```mermaid
sequenceDiagram
    actor SME as SME
    participant Frontend as Frontend
    participant KYC as KYC
    participant App as App

    Note over SME, App: Executable proof: RegistrationTest.oauth2AuthorizationCodeWithPkce

Note over SME, App: Establish the KYC browser session
    SME ->> Frontend: Open app
    Frontend ->> KYC: GET /kyc/auth/browser/session
    KYC -->> Frontend: anonymous + CSRF token
    alt Password login
        SME ->> Frontend: Enter email and password
        Frontend ->> KYC: POST /kyc/auth/browser/login + CSRF
        Note right of KYC: Validate credentials <br> Reject unverified or locked accounts
        opt TOTP is enabled
            KYC -->> Frontend: totp_required
            SME ->> Frontend: Enter TOTP or recovery code
            Frontend ->> KYC: POST /kyc/auth/browser/totp + CSRF
        end
    else Passkey login
        Frontend ->> KYC: POST /kyc/auth/browser/passkeys/authenticate/options + CSRF
        SME ->> Frontend: Verify with authenticator
        Frontend ->> KYC: POST /kyc/auth/browser/passkeys/authenticate/verify + CSRF
    end
    KYC -->> Frontend: authenticated KYC session

Note over SME, App: Obtain an access token with Authorization Code + PKCE
    Frontend ->> KYC: GET /kyc/auth/oauth2/authorize <br> ( client_id, redirect_uri, code_challenge, state, peppol_id, account_type )
    Note right of KYC: Validate ownership belongs to account <br> Store exact selection with authorization code
    KYC ->> Frontend: Redirect to /callback?code=...&state=...
    Frontend ->> KYC: POST /kyc/auth/oauth2/token <br> ( code, code_verifier )
    Note right of KYC: Revalidate stored ownership selection <br> Add claims to the access token
    KYC ->> Frontend: access_token ( accountType, peppolId, peppolActive, uid ) + id_token
    Frontend ->> App: GET /app/sapi/company
    Note right of App: Get company by JWT.peppolId
    opt company is unknown
        App -->> KYC: GET /sapi/company
        Note left of KYC: Get ADMIN account by JWT.peppolId <br> Get company by JWT.peppolId
        KYC -->> App: AccountInfo
        Note right of App: Store company
    end
    App ->> Frontend: CompanyDto
    Frontend ->> SME: Show dashboard
```

The SPA keeps tokens in memory only. On reload, or when the access token expires, it re-authorizes
silently (`prompt=none`) against the HttpOnly KYC session cookie instead of storing a refresh token.
Every contextual renewal gets its Peppol ID from the URL and its role from `sessionStorage`, so tabs
can renew different ownerships independently. A pending relative URL is also kept in
`sessionStorage` during interactive login and restored after `/callback`, including its query and
fragment.
The login page's "remember my email" option remembers only the email address; it does not persist an access
token or bypass KYC authentication.

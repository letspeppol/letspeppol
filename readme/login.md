# Login

Login runs as an OAuth2 Authorization Code flow with PKCE against KYC (the authorization server).
Credentials are entered on KYC's own login page, never in the SPA, and the access token is minted by
KYC rather than handed out by a custom endpoint. The acting ownership carried in the token is the
account's most recently used one — see [swap.md](swap.md) for changing it.

```mermaid
sequenceDiagram
    actor SME as SME
    participant Frontend as Frontend
    participant KYC as KYC
    participant App as App

Note over SME, App: Login with Authorization Code + PKCE
    SME ->> Frontend: Open app
    Frontend ->> KYC: GET /kyc/oauth2/authorize <br> ( client_id, redirect_uri, code_challenge, state )
    KYC ->> SME: Login page
    SME ->> KYC: Submit( email, password )
    Note right of KYC: Validate credentials <br> Reject unverified or locked accounts <br> Prompt for TOTP when enabled
    KYC ->> Frontend: Redirect to /callback?code=...&state=...
    Frontend ->> KYC: POST /kyc/oauth2/token <br> ( code, code_verifier )
    Note right of KYC: Resolve last used ownership for the account <br> Add claims to the access token
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
silently (`prompt=none`) against the KYC session cookie instead of storing a refresh token.

# Swap

An account can own several companies under several roles. Since access tokens are minted by KYC's
authorization server, a swap cannot hand back a new token directly: the frontend records the
selection, then re-authorizes silently to obtain a token carrying the new context.

The acting ownership is resolved at token-mint time as the account's most recently used one, so
selecting an ownership is simply an update of `lastUsed`.

```mermaid
sequenceDiagram
    actor SME as SME
    participant Frontend as Frontend
    participant KYC as KYC
    participant App as App

Note over SME, App: Swap active ownership
    SME ->> Frontend: Select ownership( AccountType, peppolId )
    Frontend ->> KYC: POST /sapi/account/ownership <br> ( AccountType, peppolId )
    Note right of KYC: Validate access token <br> Validate ownership AccountType for peppolId <br> Update last used ownership
    KYC ->> Frontend: 204 No Content
    Frontend ->> KYC: GET /kyc/oauth2/authorize ( prompt=none ) + POST /kyc/oauth2/token
    Note right of KYC: Resolve last used ownership <br> Add claims to the access token
    KYC ->> Frontend: access_token ( AccountType, peppolId, peppolActive, uid )
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

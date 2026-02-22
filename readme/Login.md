# Login

```mermaid
sequenceDiagram
    actor SME as SME
    participant Frontend as Frontend
    participant KYC as KYC
    participant App as App
    %%participant PeppolDirectory as PeppolDirectory
    %%participant Proxy as Proxy
    %%participant Peppol as Peppol

Note over SME, App: Login with credentials
    SME ->> Frontend: Login( email, password )
    Frontend ->> KYC: POST /api/jwt/auth
    Note right of KYC: Validate credentials <br> Use last used ownership <br> Update last used ownership
    KYC ->> Frontend: JWT ( lastUsed.AccountType, lastUsed.peppolId, lastUsed.peppolActive, uid )
    Frontend ->> App: GET /app/sapi/company
    Note right of App: Get company by JWT.peppolId
    opt company is unknown
        App -->> KYC: GET /sapi/company
        %% TODO : should be ADMIN ? Else first data stored is first requested Account ?
        Note left of KYC: Get account by JWT.uid <br> Get company by JWT.peppolId 
        KYC -->> App: AccountInfo
        Note right of App: Store company
    end
    App ->> Frontend: CompanyDto
    Frontend ->> SME: Show dashboard
```

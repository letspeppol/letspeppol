# Registration existing ADMIN via ACCOUNTANT and verify by email

```mermaid
sequenceDiagram
    actor SME as SME
    actor ACCOUNTANT as ACCOUNTANT
    participant Frontend as Frontend
    participant KYC as KYC
    participant App as App
    participant PeppolDirectory as PeppolDirectory
    participant Proxy as Proxy
    participant Peppol as Peppol
    
    Note left of ACCOUNTANT: Visit /accountant/companies
    ACCOUNTANT ->> Frontend: Create account( VAT, mail )
    Frontend ->> KYC: GET /kyc/api/register/company/{PeppolID}
    Note right of KYC: Find company by PeppolID <br> or do CBE lookup
    KYC ->> Frontend: CompanyResponse
    Frontend ->> ACCOUNTANT: Show company details

    Note left of ACCOUNTANT: Verify information
    ACCOUNTANT ->> Frontend: Confirm()
    Frontend ->> KYC: POST /kyc/sapi/accountant/confirm-company <br> ( AccountType.ADMIN, peppolId, email )
    Note right of KYC: PeppolID has ADMIN <br>(= registered) <br> Generate token (Requester = Accountant, type = ADMIN)
    KYC ->> SME: Mail "Confirm your accountant" /email-confirmation?token={token}
    KYC ->> Frontend: "Request email sent"
    Frontend ->> ACCOUNTANT: "Check email"

    Note left of SME: Receives email
    SME ->> Frontend: Open link in email
    Frontend ->> KYC: POST /kyc/api/register/verify?token={token}
    Note right of KYC: Validate token <br> Requester == ACCOUNTANT <br> Company != suspended <br> Type == ADMIN <br> PeppolID has ADMIN <br> Find company by PeppolID <br> or do CBE lookup
    KYC ->> Frontend: TokenVerificationResponse <br> ( email, null, Requester )
    Frontend ->> SME: Show requester

    Note left of SME: Read requester
    ACCOUNTANT ->> Frontend: Agree()
    Frontend ->> KYC: POST /kyc/sapi/approve?token={token}
    Note right of KYC: Validate token <br> Type == ADMIN <br> Link requester as ACCOUNTANT to Company
    KYC ->> Frontend: OK
    Frontend ->> SME: Show success
```

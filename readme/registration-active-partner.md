# Registration active PARTNER

```mermaid
sequenceDiagram
    actor PARTNER as PARTNER
    participant Frontend as Frontend
    participant KYC as KYC

Note over PARTNER, KYC: Requesting registration for active PARTNER
    Note left of PARTNER: Visit /partner/registration
    PARTNER ->> Frontend: Create account( VAT, mail )
    Frontend ->> KYC: GET /kyc/api/register/company/{PeppolID}
    Note right of KYC: Find company by PeppolID <br> or do CBE lookup
    KYC ->> Frontend: CompanyResponse <br> with hasAdmin == true
    Frontend ->> PARTNER: Show company already registered <br> and contact your administrator
```

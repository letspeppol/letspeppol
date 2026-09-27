# Registration active ADMIN via AFFILIATE and verify by email

Executable proof: [`RegistrationTest`](../kyc/src/test/java/org/letspeppol/kyc/controller/RegistrationTest.java), method `registrationActiveAdminViaAffiliateAndVerifyByEmail`.

```mermaid
sequenceDiagram
    actor SME as SME
    actor AFFILIATE as AFFILIATE
    participant Frontend as Frontend
    participant KYC as KYC
    participant App as App

    Note over SME, App: Executable proof: RegistrationTest.registrationActiveAdminViaAffiliateAndVerifyByEmail

Note over SME, App: Requesting active company added to AFFILIATE
    Note left of AFFILIATE: Visit /affiliate/companies
    AFFILIATE ->> Frontend: Add account( VAT, mail )
    Frontend ->> KYC: GET /kyc/api/register/company/{PeppolID}
    Note right of KYC: Find company by PeppolID <br> or do CBE lookup
    KYC ->> Frontend: CompanyResponse <br> with hasAdmin == true
    Frontend ->> AFFILIATE: Show company details

    Note left of AFFILIATE: Verify information
    AFFILIATE ->> Frontend: Confirm()
    Frontend ->> KYC: POST /kyc/sapi/linked/request-company <br> Authorization: Bearer AFFILIATE_JWT <br> ( AccountType.ADMIN, peppolId, email, <br> city, postCode, street )
    Note right of KYC: JWT == AFFILIATE <br> PeppolID has ADMIN <br>(= registered) <br> Generate token (Requester = Affiliate, type = ADMIN)
    KYC ->> SME: Mail "Confirm your affiliate" /email-confirmation?token={token}
    KYC ->> Frontend: "Request email sent"
    Frontend ->> AFFILIATE: "Company already active" & "Email is sent"

Note over SME, App: Reviewing the requester as the existing ADMIN
    Note left of SME: Receives email
    SME ->> Frontend: Open link in email
    Frontend ->> KYC: POST /kyc/api/register/verify?token={token}
    Note right of KYC: Validate token <br> Requester == AFFILIATE <br> Type == ADMIN <br> PeppolID has ADMIN
    KYC ->> Frontend: TokenVerificationResponse <br> ( email, accountExists, accountVerified, <br> directorSigned, requestedType, CompanyResponse, requester )
    Frontend ->> SME: Show requester for CompanyResponse <br> with login form for email, CompanyResponse.peppolId <br> and AccountType.ADMIN

    Note left of SME: Read requester
    SME ->> Frontend: LoginToConfirm( email, password )
    Frontend ->> KYC: OAuth2 Authorization Code + PKCE (see login.md) <br> acting ownership = ( AccountType.ADMIN, peppolId )
    Note right of KYC: Validate credentials <br> Validate ownership ADMIN for peppolId <br> Update last used ownership
    KYC ->> Frontend: JWT ( AccountType.ADMIN, peppolId, peppolActive, uid )
    Note right of Frontend: Current implementation can display requester context. <br> No affiliate approval mutation endpoint exists yet.
    Frontend ->> SME: Show requester and company context
```

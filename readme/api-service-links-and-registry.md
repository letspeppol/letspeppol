# Service links, registry control, and sponsor invoices

This flow groups authenticated service-link settings with service-scoped registry operations and
sponsor-invoice submission. It distinguishes the user's access token from a service token carrying
the acting-user authorization header.

Executable proof: [`RegistrationTest`](../kyc/src/test/java/org/letspeppol/kyc/controller/RegistrationTest.java) proves KYC's registry call; [`SponsorInvoiceServiceTest`](../app/backend/src/test/java/org/letspeppol/app/service/SponsorInvoiceServiceTest.java) proves client-credentials document submission; Proxy and KYC OpenAPI tests cover the management routes.

```mermaid
sequenceDiagram
    actor Admin
    participant UI as Frontend
    participant App
    participant KYC
    participant Proxy
    participant AP as Access Point

    Note over Admin,AP: Proof: RegistrationTest<br/>SponsorInvoiceServiceTest<br/>KYC and Proxy OpenApiDocumentationTest
    UI->>KYC: GET /kyc/sapi/linked
    UI->>KYC: POST /kyc/sapi/linked/register
    UI->>KYC: POST /kyc/sapi/linked/unregister
    opt App company setting enables or disables email integration
        UI->>App: PUT /app/sapi/company
        App->>KYC: POST /kyc/sapi/linked/register (forward user token)
        App->>KYC: POST /kyc/sapi/linked/unregister (forward user token)
    end
    KYC->>KYC: POST /kyc/auth/oauth2/token (client_credentials, service)
    KYC->>Proxy: GET or POST /proxy/sapi/registry
    KYC->>Proxy: PUT /proxy/sapi/registry/unregister, /allow, or /reject
    KYC->>Proxy: DELETE /proxy/sapi/registry
    Proxy->>AP: apply registry operation
    opt Authenticated sponsor invoice
        UI->>App: POST /app/sapi/sponsors (user token)
        App->>KYC: POST /kyc/auth/oauth2/token (client_credentials, service)
        App->>Proxy: POST /proxy/sapi/document<br/>Bearer service token + X-Acting-User-Authorization
    end
```

Registry route coverage: `/proxy/sapi/registry/allow` and `/proxy/sapi/registry/reject` are the two
app-link decisions represented by "apply registry operation".

Return to the [API network flow guide](./api-network-flows.md).

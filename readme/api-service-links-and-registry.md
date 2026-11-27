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
    KYC->>Proxy: GET /proxy/sapi/registry/registration-status
    KYC->>Proxy: GET or PUT /proxy/sapi/registry/settings
    KYC->>Proxy: GET /proxy/sapi/registry/receiving-availability
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
`/proxy/sapi/registry/registration-status` also reports the configured default provider;
`/proxy/sapi/registry/settings` reads or updates provider-specific receiving settings.
`/proxy/sapi/registry/receiving-availability` uses the configured local SMP when the
default provider is PHOSS, otherwise Recommand discovery. PHOSS reports metadata
availability without claiming that LetsPeppol receiving is enabled. It does not
expose AP credentials or internal SMP URLs in its response.

PHOSS registration is initially a local **send-only association**, not public SMP
registration. Receiving cannot be enabled through this adapter yet. See the
[PHOSS integration guide](./phoss-access-point.md) for transport and local-lab limits.
Focused proof: [`PhossServiceTest`](../proxy/src/test/java/org/letspeppol/proxy/service/PhossServiceTest.java).

Return to the [API network flow guide](./api-network-flows.md).

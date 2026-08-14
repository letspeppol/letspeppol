# Business-document lifecycle and Peppol transport

This flow follows a business document from validation and local persistence through Proxy transport,
status synchronization, download acknowledgement, cancellation, and user-visible state changes.

Executable proof: [`DocumentNetworkFlowTest`](../app/backend/src/test/java/org/letspeppol/app/service/DocumentNetworkFlowTest.java) covers App-to-Proxy calls, while [`AppControllerTest`](../proxy/src/test/java/org/letspeppol/proxy/controller/AppControllerTest.java) covers Proxy's acting-user validation. OpenAPI documentation tests guard every route in the grouped notes.

```mermaid
sequenceDiagram
    actor User
    participant UI as Frontend
    participant App
    participant KYC
    participant Proxy
    participant AP as Peppol Access Point

    Note over User,AP: Proof: DocumentNetworkFlowTest<br/>Proxy AppControllerTest<br/>App and Proxy OpenApiDocumentationTest
    UI->>App: POST /app/sapi/document/validate
    UI->>App: GET /app/sapi/document and /{id}, /{id}/details, /{id}/pdf
    UI->>App: POST or PUT /app/sapi/document, /{id}
    UI->>App: PUT /app/sapi/document/{id}/send or /reschedule
    App->>Proxy: POST or PUT /proxy/sapi/document, /{id} (forward user token)
    App->>Proxy: PUT /proxy/sapi/document/{id}/reschedule
    Proxy->>AP: send UBL document
    UI->>App: PUT /app/sapi/document/{id}/read, /paid, /error-seen
    UI->>App: DELETE /app/sapi/document/{id}
    App->>Proxy: DELETE /proxy/sapi/document/{id} when transport cancellation is needed
    loop User refresh or background synchronization
        App->>KYC: POST /kyc/oauth2/token (client_credentials for background jobs)
        App->>Proxy: GET /proxy/sapi/document and /{id}, /{id}/details
        App->>Proxy: POST /proxy/sapi/document/status
        App->>Proxy: PUT /proxy/sapi/document/{id}/downloaded or /downloaded
    end
```

Document route coverage: the grouped App reads and actions include
`/app/sapi/document/{id}/details`, `/app/sapi/document/{id}/pdf`,
`/app/sapi/document/{id}/reschedule`, `/app/sapi/document/{id}/paid`, and
`/app/sapi/document/{id}/error-seen`. Proxy polling includes
`/proxy/sapi/document/{id}/details` and the batch acknowledgement route
`/proxy/sapi/document/downloaded`.

Return to the [API network flow guide](./api-network-flows.md).

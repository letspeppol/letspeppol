# Public discovery and aggregate statistics

This flow groups unauthenticated discovery and aggregate-statistics APIs. App caches or refreshes
third-party aggregate data; Proxy exposes its own registered-company count.

Executable proof: [`PublicApiNetworkFlowTest`](../app/backend/src/test/java/org/letspeppol/app/service/PublicApiNetworkFlowTest.java) proves Peppol Directory and Proxy aggregate calls, and [`StatsControllerTest`](../proxy/src/test/java/org/letspeppol/proxy/controller/StatsControllerTest.java) proves the public Proxy result. The App and Proxy OpenAPI tests validate route coverage.

```mermaid
sequenceDiagram
    actor Visitor
    participant UI as Frontend or public site
    participant App
    participant Directory as Peppol Directory
    participant Collective as Open Collective
    participant Proxy

    Note over Visitor,Proxy: Proof: PublicApiNetworkFlowTest<br/>Proxy StatsControllerTest<br/>App and Proxy OpenApiDocumentationTest
    Visitor->>UI: Open public page or registration search
    UI->>App: GET /app/api/sponsors
    UI->>App: GET /app/api/sponsors/contributions
    UI->>App: GET /app/api/stats/donation
    App->>Collective: refresh aggregate donation data (scheduled/cached)
    UI->>App: GET /app/api/peppol-directory?participant=... or ?name=...
    App->>Directory: GET /search/1.0/json
    UI->>Proxy: GET /proxy/api/stats
    Proxy-->>UI: aggregate registered-company count
```

Return to the [API network flow guide](./api-network-flows.md).

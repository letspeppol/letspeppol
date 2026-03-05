# Shopify App Integration Plan for Let's Peppol

This document outlines the strategy for integrating a Shopify Application with the Let's Peppol ecosystem, enabling merchants to send e-invoices directly from their Shopify store.

## 1. High-Level Architecture

The integration follows a "Delegated Authority" model:

- **Shopify App**: Acts as the document generator and orchestrator.
- **Let's Peppol Proxy**: Acts as the Peppol Access Point gateway.
- **Authentication**: Uses APP-type JWT tokens with cross-referencing via `AppLink`.

---

## 2. Step-by-Step Implementation

### Phase 1: Merchant Onboarding & Authorization

1.  **Legal Hub Registration**: The merchant must register on `be.letspeppol.org` to complete KYC (Identity verification via eID).
2.  **App Authorization**: Inside the Let's Peppol Account dashboard, the merchant "links" their Peppol ID to your Shopify App's Unique Identifier (UID). This creates an entry in the `proxy.app_link` table.

### Phase 2: UBL Generation (Shopify side)

1.  Map Shopify `Order` and `Customer` objects to **Peppol BIS Billing 3.0** (UBL 2.1).
2.  **Lookup**: Use the Peppol Directory API to retrieve the receiver's `peppolId` based on their VAT number.

### Phase 3: Sending the Document

1.  **APP Login**: The Shopify app authenticates with the `kyc` service using its own `external_id` (UID) and secret to get an `APP` type JWT.
2.  **API Call**: Submit the invoice to the proxy.
    - **Endpoint**: `POST /proxy/sapi/document`
    - **Payload**: Includes the UBL content, the merchant's `peppolId` (owner), and document metadata.

---

## 3. Required Backend Modifications (Let's Peppol Core)

To enable this flow, the following changes are required in the `proxy` service:

### A. `JwtUtil.java` Refactoring

Currently, `getUserPeppolId` strictly rejects non-user accounts. It should be updated to handle `APP` accounts that are authorized to act for a specific `peppolId`.

```java
// Logic to be added:
// If accountType is APP, allow the request to proceed if the specific
// target Peppol ID is authorized via the AppLink table.
```

### B. `AppController.java` Validation Update

The `validateSender` method must be adjusted to allow `APP` tokens to send documents where the `ownerPeppolId` matches an authorized `AppLink` for that App's UID.

```java
private void validateSender(Jwt jwt, UblDocumentDto ublDocumentDto) {
    AccountType type = JwtUtil.getAccountType(jwt);
    if (type.isApp()) {
        // Verify that ublDocumentDto.ownerPeppolId() is linked to this App's UID
    } else {
        // Existing user-based validation
    }
}
```

---

## 4. Next Steps

1.  [ ] Implement `AppLink` management UI in `app/ui`.
2.  [ ] Patch `proxy` service validation logic to support `APP` account delegates.
3.  [ ] Create a Shopify-specific UBL template library.

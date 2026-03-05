# Shopify App Integration with Peppol

To integrate your Shopify app with **Let's Peppol**, you essentially need to build a bridge between Shopify's order data and the Peppol network's UBL format.

Based on the current architecture of your codebase, here is the recommended solution and implementation steps:

## 1. High-Level Architecture

- **Shopify App**: Extracts order data and generates a **UBL 2.1 (Peppol BIS Billing 3.0)** document.
- **Let's Peppol Proxy**: Acts as your API gateway. Your Shopify app will "talk" to the proxy service to send the invoice.
- **Security**: Your Shopify app will authenticate as an **`APP`** type account using a JWT token.

## 2. Step-by-Step Implementation

### Step 1: Merchant Onboarding

Because Peppol requires legal identity verification (KYC), the merchant must first go through the registration process on your portal (**be.letspeppol.org**).

1.  Merchant registers their company using their Belgian eID.
2.  Merchant activates their Peppol ID.
3.  **New Feature needed**: You should add a specialized **"API/Integrations"** tab in the **Account** UI where the merchant can "Authorize my Shopify App." This will create an **`AppLink`** in the proxy database.

### Step 2: Build the UBL Generator in Shopify

In your Shopify app, you need to convert a Shopify Order into a Peppol-compliant XML.

- **Format**: Peppol BIS Billing 3.0.
- **Supplier**: The merchant (using their verified `peppolId`).
- **Customer**: The buyer (you must look up their `peppolId` via the Peppol Directory).

### Step 3: Authenticate your App

Your Shopify app needs its own credentials in the `kyc` database (an `external_id` and a `password`).

1.  Your app calls the `kyc` service to obtain an **APP-level JWT**.
2.  This JWT allows you to perform actions on behalf of any merchant who has "linked" their account to you.

### Step 4: The "Send" Request

Once you have the UBL XML and the JWT, your Shopify app sends a request to the proxy:

- **URL**: `POST https://be.letspeppol.org/proxy/sapi/document`
- **Body**:

```json
{
  "ownerPeppolId": "iso6523-actorid-upis::0190:123456789", // The Merchant
  "ubl": "<xml>...</xml>",
  "direction": "OUTGOING",
  "type": "INVOICE"
}
```

## 3. Required Code Changes (The "Solution")

Currently, the proxy service's "Send" logic is strictly limited to `USER` accounts. To support your Shopify App (an `APP` account), you need to make these two modifications:

### A. Modify `AppController.java`

In `proxy`, the `validateSender` method needs to be updated. It currently checks `JwtUtil.getUserPeppolId(jwt)`, which throws an error if the token is an `APP` type.

- **Change**: Update the validation to check if the `APP` has a valid **`AppLink`** to the `ownerPeppolId` provided in the request.

### B. Update `JwtUtil.java`

Update `getUserPeppolId` to allow tokens with `AccountType.APP` if they are authorized to act for that merchant.

---

## Summary

- **UI**: Add an "Integrated Apps" section to the Account page.
- **Proxy**: Patch **`AppController`** to allow `APP` accounts to send on behalf of linked `USER` accounts.
- **Shopify**: Generate UBL from Shopify Orders and POST it to the proxy.

> **Focus on "Send" first**: Start by implementing the `POST /sapi/document` flow. "Receive" can be handled later by polling the `GET /sapi/document` endpoint using your App's token.

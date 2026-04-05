# KYC

## Purpose

The KYC (Know-Your-Customer) module exists to verify who is requesting access to Let’s Peppol and whether that party can legitimately act on behalf of a business.

For Let’s Peppol, KYC is not just an administrative step. It is a trust and risk control layer around access to the Peppol network. Because electronic invoicing can have legal, financial, and operational consequences, we must reduce the risk of:

- fraudulent business registrations
- impersonation of companies or representatives
- unauthorized access to invoice flows
- abuse of the platform for fake invoicing or identity misuse
- inconsistent or incomplete business identity data

KYC helps ensure that the party registering is real, that the represented organization can be identified, and that the link between user and organization is sufficiently trustworthy for the intended level of access.

## Why KYC matters for Let’s Peppol

Let’s Peppol operates in a context where trust, traceability, and correct business identification matter. A company using the platform may send or receive structured invoices that affect accounting, tax processing, payment expectations, and audit trails.

Without KYC, the platform would be far more vulnerable to misuse, including:

- registration of fake entities
- registration by someone with no valid connection to the company
- attempts to onboard companies using incomplete or manipulated data
- confusion around who is authorized to manage invoicing settings
- support and compliance issues caused by unverifiable registrations

The KYC module is therefore designed to support:

- stronger platform trust
- safer onboarding
- better traceability of who requested access
- clearer linkage between natural persons and legal entities
- more controlled activation of Peppol-related capabilities

## Scope of this module

This module covers the registration and verification flow required before an organization can be accepted into Let’s Peppol through the KYC process.

It focuses on:

- identifying the registering party
- collecting required business information
- validating the registration flow step by step
- determining whether the registration can proceed
- creating a structured audit trail for the onboarding process

## API overview

The KYC API is organized as a sequence of steps. Each step represents part of the onboarding and verification process. The flows are documented separately using Mermaid sequence diagrams.

The pages below should be read in the same order as the scenarios covered by `AdminRegistrationTest` in the KYC project.

## Flow documentation

1. [Registration new ADMIN](./registration-new-admin.md)
2. [Registration active ADMIN](./registration-active-admin.md)
3. [Registration new ACCOUNTANT](./registration-new-accountant.md)
4. [Registration active ACCOUNTANT](./registration-active-accountant.md)
5. [Login](./login.md)
6. [Swap](./swap.md)
7. [Registration active ADMIN via ACCOUNTANT and verify by email](./registration-active-admin-via-accountant-and-verify-by-email.md)
8. [Registration new ADMIN via ACCOUNTANT and verify email before signing](./registration-new-admin-via-accountant-and-verify-email-before-signing.md)
9. [Registration new ADMIN via ACCOUNTANT and sign before email verification](./registration-new-admin-via-accountant-and-sign-before-email-verification.md)

## How to read the flow pages

Each linked page documents one API flow and should describe:

- the purpose of the step
- the actors involved
- the expected request
- the expected response
- validation or business rules
- possible failure paths
- the transition to the next step in the process

These pages mainly serve to explain the operational meaning of each flow and how it fits into the overall KYC lifecycle.

## Design principles

The KYC module is based on a few simple principles:

### 1. Trust must be built before access is granted
Access to a business invoicing environment should not be activated before the requesting party and organization have gone through the required checks.

### 2. Each step should be traceable
Every important registration action should be understandable and auditable afterward.

### 3. Validation should happen as early as possible
Incorrect, incomplete, or suspicious input should be detected early in the flow to avoid ambiguity later.

### 4. The API should remain sequential and predictable
The registration process should follow a clear order, with each step depending on the outcome of the previous one.

### 5. Documentation should mirror test behavior
The documented order of flows should match the order used in `AdminRegistrationTest`, so the implementation, tests, and documentation remain aligned.

## Intended audience

This documentation is intended for:

- developers integrating with the KYC API
- maintainers of the Let’s Peppol onboarding flow
- reviewers who need to understand why KYC exists in the platform
- testers validating the documented sequence against implementation behavior

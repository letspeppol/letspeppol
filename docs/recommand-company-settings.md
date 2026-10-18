# Recommand company settings

The Account page has two Peppol checkboxes: **Allow sending** and **Receive Peppol Documents in Let’s Peppol**. Changes use the main Save action. Removing sending also removes receiving; confirmation modals explain the consequences.

Self-billing is part of the standard receiving setup. There is no separate option to disable it. Every company is configured for ordinary invoices and credit notes plus self-billed invoices and credit notes, including companies that currently send only. Turning receiving off stops delivery of all four types until it is turned on again.

## Only the 0208 address

Recommand's automatic Belgian setup creates both an enterprise-number address (`0208`) and a VAT-number address (`9925`). Supplying the VAT number alone therefore does not control which address is published. The documented way to choose identifiers is `skipDefaultCompanySetup: true`, followed by the identifiers and document-types endpoints.

New registrations follow this sequence:

1. Create the company with its VAT number, `isSmpRecipient: false`, and `skipDefaultCompanySetup: true`.
2. Create only the company's `0208` identifier and the four document registrations.
3. Verify the signed contract.
4. Check availability of **only the 0208 address**. If it is available, set `isSmpRecipient: true`; otherwise leave the company send-only. An unavailable lookup also leaves it send-only.

The VAT number remains company data for invoices and verification. The proxy never creates or checks a `9925` identifier during this registration flow. A failure before registration completes deletes the newly created company so it can be retried. A recipient conflict at final activation leaves the verified company send-only.

Companies currently registered at Scrada use only the `0208` address. Moving a company to Recommand uses the same complete registration flow above, so its identifiers and all four document types are configured at creation.

## Stored settings

Settings are persisted in dedicated columns in the proxy's `registry` table:

| Column | Meaning |
| --- | --- |
| `access_point` | Active sending provider; `NONE` means sending is disabled. |
| `receive_documents` | `true`: sending and receiving; `false`: send-only for an active provider; `NULL`: receiving state needs reconciliation. |

`variables` contains only provider registration response data, such as Recommand's company ID and verification URL. Flyway migration `V1.7__registry_receiving_settings.sql` adds the receiving boolean and initializes existing Scrada/e-invoice companies as receiving, and inactive registrations as not receiving.

New registrations populate the receiving flag directly. Normal reads of a known flag make no Recommand requests and do not rewrite the database. If receiving is unknown, only company details need fetching once. Settings are managed through the proxy. Identifier and document-type setup happens during registration.

Settings operations lock the registry row. Before changing receiving remotely, the flag is cleared; success stores the confirmed value. If the write fails or its outcome is uncertain, that invalidation is committed, so the next request fetches the actual receiving state. Enabling receiving checks the live 0208 registration because another provider may have claimed it since the last check. Unregistering clears receiving; registering or moving to a provider fills fresh settings.

## Internal API

The proxy exposes `GET` and `PUT /sapi/registry/settings?peppolId=...` to trusted services. The application exposes `GET` and `PUT /sapi/company/peppol-registration/settings`; identity comes from the JWT and writes require `accountType=ADMIN`.

```json
{"receiveDocuments": true}
```

Omitting the field leaves receiving unchanged.

## Deferred document UI

This change configures network reception. Viewing and creating self-billing documents in the application remains the separate implementation agreed earlier: it requires document types, validation, reversed buyer/seller transport roles, financial handling, and editor changes. Incoming XML is retained at the proxy/access point during this phase.

## Research

- [Belgian identifiers and default document registrations](https://docs.recommand.eu/getting-started/belgium/platform/sending-and-receiving)
- [Managing companies and skipDefaultCompanySetup](https://docs.recommand.eu/docs/managing-companies)
- [Create company identifier](https://docs.recommand.eu/reference/company-identifiers/create-company-identifier)
- [Peppol self-billing identifiers](https://docs.peppol.eu/poacc/self-billing/3.0/bis-sb/)

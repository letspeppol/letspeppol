import type { Meta, Story } from '../support/story';
import { Invoices } from '../../src/invoice/invoices';
import { DocumentType } from '../../src/services/app/invoice-service';
import { CompanyPermission } from '../../src/services/app/company-permission';
import type { Invoice } from '../../src/services/peppol/ubl';
import {
  docs,
  failedValidation,
  invoiceLines,
  invoiceMocks,
  openDocument,
  partyOf,
  seller,
  startNewDocument,
  type InvoiceStoryOptions,
} from '../support/mocks/invoices';
import { clickWhenReady, ready } from './helpers';

function screen(options: InvoiceStoryOptions = {}) {
  return () => ({ items: invoiceMocks(options) });
}

const phone = { viewport: { value: 'phone', isRotated: false } };

const meta: Meta = {
  title: 'Screens/Invoices/Editor',
  component: Invoices,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 1100 },
      description: {
        component:
          'The editor is one sheet: four tiles across the top (document, dates, customer, payment), attachments and discounts beside them, the lines in the middle and the totals at the foot. ' +
          'A tile with a missing required value gets a red border; Send stays disabled until every tile is complete, and the server validation result is shown before anything leaves. ' +
          'Sent, received, uploaded and self-billed documents open read-only: the edit pencils disappear and the actions change to delivery proof, credit note and payment status. ' +
          'Under 1024px the actions move into a sticky bar at the bottom; on phones the secondary actions fold into the More menu.',
      },
    },
  },
};

export default meta;

export const NewInvoice: Story = {
  name: 'New invoice: choose a customer',
  parameters: { docs: { description: { story: 'Clicking New invoice opens the editor with the customer dialog on top, the first thing an invoice needs.' } } },
  render: screen(),
  play: async ({ canvasElement }) => {
    await ready(canvasElement, '.invoice-table tbody tr');
    await clickWhenReady(canvasElement, '.overview-actions .btn.primary');
    await ready(canvasElement, '#customer-modal-title');
  },
};

export const NewInvoiceInProgress: Story = {
  name: 'New invoice being filled in',
  parameters: { docs: { description: { story: 'Not saved yet: the number tile shows the number it will get, and Save draft and Send invoice sit side by side.' } } },
  render: screen({
    setup: startNewDocument(DocumentType.INVOICE, (doc) => {
      doc.AccountingCustomerParty.Party = structuredClone(partyOf.mosan);
      doc.BuyerReference = 'MOSAN-BQT-2240';
      (doc as Invoice).InvoiceLine = invoiceLines([
        { name: 'Broodjeslunch per persoon', description: 'Drie belegde broodjes, soep en fruit', quantity: 18, price: 14.5, vat: 12 },
        { name: 'Croissant roomboter', quantity: 36, price: 1.45, vat: 6 },
      ]);
    }),
  }),
};

export const Draft: Story = {
  name: 'Saved draft',
  parameters: { docs: { description: { story: 'A draft with mixed VAT rates, a line charge, a document discount and three attachments; delete lives in the More menu.' } } },
  render: screen({ setup: openDocument(docs.draft) }),
};

export const ValidationErrors: Story = {
  name: 'Draft with missing information',
  parameters: { docs: { description: { story: 'No reference, a due date in the past, no IBAN, no legal name and empty lines: each tile and cell that blocks sending is outlined in red.' } } },
  render: screen({ setup: openDocument(docs.incompleteDraft) }),
};

export const RejectedByValidation: Story = {
  name: 'Send blocked by validation',
  parameters: { docs: { description: { story: 'The draft looks complete, but the Peppol validator still finds problems; the list comes back before anything is sent.' } } },
  render: screen({ setup: openDocument(docs.draft), validation: failedValidation }),
  play: async ({ canvasElement }) => {
    await clickWhenReady(canvasElement, '.editor-action-bar .btn.primary:not([disabled])');
    await clickWhenReady(canvasElement, '[aria-labelledby="number-modal-title"] .btn.primary:not([disabled])');
    await ready(canvasElement, '.validation-errors li');
  },
};

export const IntraCommunity: Story = {
  name: 'Intra-community supply at 0% VAT',
  parameters: { docs: { description: { story: 'Lines with an exemption reason need a delivery date and country, which then show in the dates tile.' } } },
  render: screen({ setup: openDocument(docs.intraCommunityDraft) }),
};

export const DraftCreditNote: Story = {
  name: 'Credit note draft',
  parameters: { docs: { description: { story: 'A credit note has no due date and refers to the invoice it corrects; the VAT note is filled in by default.' } } },
  render: screen({ setup: openDocument(docs.draftCreditNote) }),
};

export const Sent: Story = {
  name: 'Sent invoice',
  parameters: { docs: { description: { story: 'Read-only once delivered: delivery proof, a credit note for this invoice and the payment status are the remaining actions.' } } },
  render: screen({ setup: openDocument(docs.sent) }),
};

export const Paid: Story = {
  name: 'Paid invoice',
  render: screen({ setup: openDocument(docs.paid) }),
};

export const Scheduled: Story = {
  name: 'Scheduled invoice',
  parameters: { docs: { description: { story: 'Accepted but throttled: the status line says when it goes out.' } } },
  render: screen({ setup: openDocument(docs.scheduled) }),
};

export const DeliveryError: Story = {
  name: 'Delivery error',
  parameters: { docs: { description: { story: 'The banner says what went wrong in the network’s own words and offers to reuse the content or acknowledge the error.' } } },
  render: screen({ setup: openDocument(docs.errored) }),
};

export const SentCreditNote: Story = {
  name: 'Sent credit note',
  render: screen({ setup: openDocument(docs.creditNote) }),
};

export const Received: Story = {
  name: 'Received invoice',
  parameters: { docs: { description: { story: 'An incoming invoice: the partner tile is the supplier, and the payment tile offers a SEPA QR code to pay it.' } } },
  render: screen({ setup: openDocument(docs.received) }),
};

export const SelfBilled: Story = {
  name: 'Uploaded self-billed invoice',
  parameters: { docs: { description: { story: 'Self-billing is upload-only: read-only, marked Uploaded, and the unknown supplier can be saved as a partner from the More menu.' } } },
  render: screen({ setup: openDocument(docs.selfBilled) }),
};

export const ReadOnlyUser: Story = {
  name: 'Draft seen by a read-only user',
  parameters: { docs: { description: { story: 'Without the draft permission even a draft opens read-only, and Send and Save are absent rather than disabled.' } } },
  render: screen({
    session: { company: seller, admin: false, permissionMask: CompanyPermission.INVOICE_READ },
    setup: openDocument(docs.draft),
  }),
};

export const Tablet: Story = {
  name: 'Tablet portrait',
  parameters: { docs: { description: { story: 'Tiles wrap two by two and the actions sit in the sticky bar.' } } },
  render: screen({ setup: openDocument(docs.draft) }),
  globals: { viewport: { value: 'tabletPortrait', isRotated: false } },
};

export const PhoneDraft: Story = {
  name: 'Phone with sticky action bar',
  parameters: { docs: { description: { story: 'Save and Send stay within thumb reach above the bottom bar; the line table becomes cards.' } } },
  render: screen({ setup: openDocument(docs.draft) }),
  globals: phone,
};

export const PhoneMoreMenu: Story = {
  name: 'Phone with the More menu open',
  render: screen({ setup: openDocument(docs.sent) }),
  globals: phone,
  play: async ({ canvasElement }) => {
    await ready(canvasElement, '.composer');
    const more = canvasElement.querySelector<HTMLElement>('.editor-more-toggle');
    if (more?.getClientRects().length) {
      await clickWhenReady(canvasElement, '.editor-more-toggle');
    }
  },
};

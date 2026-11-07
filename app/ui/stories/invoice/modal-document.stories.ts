import type { Meta, Story } from '../support/story';
import { InvoiceModal } from '../../src/invoice/edit/components/modals/invoice-modal';
import { archiveDocuments, docs, openDocument } from '../support/mocks/invoices';
import { modalFrame, part } from './helpers';

const markup = '<invoice-modal invoice-context.bind="invoiceContext" original-document-type.bind="invoiceContext.selectedDocumentType" open-modal></invoice-modal>';

const meta: Meta = {
  title: 'Invoice editor/Modals/Document details',
  component: InvoiceModal,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: modalFrame,
      description: {
        component:
          'Opened from the first tile: document type, number, buyer and order reference, and an optional note. ' +
          'Peppol needs a buyer reference or an order reference, so Apply stays disabled until one of them is filled in. ' +
          'Switching to credit note adds the invoice it corrects, picked from this customer’s delivered invoices, and opens the note with the standard VAT sentence. ' +
          'Centred on desktop, a bottom sheet on phones.',
      },
    },
  },
};

export default meta;

export const Invoice: Story = {
  name: 'Invoice',
  render: part(markup, { setup: openDocument(docs.draft) }),
};

export const MissingReference: Story = {
  name: 'Reference missing',
  render: part(markup, { setup: openDocument(docs.incompleteDraft) }),
};

export const CreditNote: Story = {
  name: 'Credit note with the corrected invoice',
  render: part(markup, { documents: archiveDocuments, setup: openDocument(docs.draftCreditNote) }),
};

export const Phone: Story = {
  name: 'Phone bottom sheet',
  render: part(markup, { setup: openDocument(docs.draft) }),
  globals: { viewport: { value: 'phone', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 844 } } },
};

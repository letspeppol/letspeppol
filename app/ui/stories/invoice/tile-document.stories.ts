import { fn } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { InvoiceInfo } from '../../src/invoice/edit/components/tiles/invoice-info';
import { docs, openDocument, startNewDocument } from '../support/mocks/invoices';
import { DocumentType } from '../../src/services/app/invoice-service';
import { part, tile } from './helpers';

const markup = tile('<invoice-info read-only.bind="invoiceContext.readOnly" show-invoice-modal.bind="showInvoiceModal"></invoice-info>');

const meta: Meta = {
  title: 'Invoice editor/Tiles/Document',
  component: InvoiceInfo,
  args: { showInvoiceModal: fn() },
  parameters: {
    docs: {
      description: {
        component:
          'The first tile identifies the document: its type as title, the number and the buyer or order reference Peppol requires. ' +
          'Until the document is sent the number is a muted preview of the next number in the series; the real number is fixed when sending. ' +
          'A credit note also shows the invoice it corrects. A missing reference marks the tile red.',
      },
    },
  },
};

export default meta;

export const NextNumber: Story = {
  name: 'Draft with the next number previewed',
  render: part(markup, { setup: openDocument(docs.draft) }),
};

export const Numbered: Story = {
  name: 'Sent invoice',
  render: part(markup, { setup: openDocument(docs.sent) }),
};

export const CreditNote: Story = {
  name: 'Credit note with the corrected invoice',
  render: part(markup, { setup: openDocument(docs.creditNote) }),
};

export const NewCreditNote: Story = {
  name: 'New credit note',
  render: part(markup, { setup: startNewDocument(DocumentType.CREDIT_NOTE) }),
};

export const MissingReference: Story = {
  name: 'Reference missing',
  parameters: { docs: { description: { story: 'Neither buyer nor order reference: the tile turns red until one is filled in.' } } },
  render: part(markup, { setup: openDocument(docs.incompleteDraft) }),
};

export const SelfBilled: Story = {
  name: 'Self-billed invoice',
  render: part(markup, { setup: openDocument(docs.selfBilled) }),
};

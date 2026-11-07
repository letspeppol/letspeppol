import type { Meta, Story } from '../support/story';
import { InvoiceDateModal } from '../../src/invoice/edit/components/modals/invoice-date-modal';
import { docs, openDocument } from '../support/mocks/invoices';
import { modalFrame, part } from './helpers';

const markup = '<invoice-date-modal invoice-context.bind="invoiceContext" document-type.bind="invoiceContext.selectedDocumentType" open-modal></invoice-date-modal>';

const meta: Meta = {
  title: 'Invoice editor/Modals/Dates',
  component: InvoiceDateModal,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: modalFrame,
      description: {
        component:
          'Issue date, payment terms and due date, kept in step: changing the terms moves the due date and picking a due date selects the matching terms. ' +
          'A due date before the issue date or in the past is explained under the field and blocks Apply. ' +
          'Credit notes only have an issue date; lines exempt for intra-community supply or export add delivery date and country.',
      },
    },
  },
};

export default meta;

export const Invoice: Story = {
  name: 'Invoice with payment terms',
  render: part(markup, { setup: openDocument(docs.draft) }),
};

export const DueBeforeIssue: Story = {
  name: 'Due date before the issue date',
  render: part(markup, { setup: openDocument(docs.incompleteDraft) }),
};

export const Delivery: Story = {
  name: 'With delivery date and country',
  render: part(markup, { setup: openDocument(docs.intraCommunityDraft) }),
};

export const CreditNote: Story = {
  name: 'Credit note',
  render: part(markup, { setup: openDocument(docs.draftCreditNote) }),
};

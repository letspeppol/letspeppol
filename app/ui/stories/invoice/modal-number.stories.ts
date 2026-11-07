import { fn } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { InvoiceNumberModal } from '../../src/invoice/edit/components/modals/invoice-number-modal';
import { docs, openDocument, seller } from '../support/mocks/invoices';
import { modalFrame, part } from './helpers';

const markup = '<invoice-number-modal invoice-context.bind="invoiceContext" open-modal.bind="[send]"></invoice-number-modal>';

const meta: Meta = {
  title: 'Invoice editor/Modals/Number',
  component: InvoiceNumberModal,
  args: { send: fn() },
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: modalFrame,
      description: {
        component:
          'Asked right before sending a document that has no number yet. ' +
          'The field is prefilled with the next number in the series and the line above it shows the last number used, so a gap or a typo is easy to spot. ' +
          'The primary button sends; it repeats the action of the button that opened the dialog.',
      },
    },
  },
};

export default meta;

export const Invoice: Story = {
  name: 'Next invoice number',
  render: part(markup, { setup: openDocument(docs.draft) }),
};

export const CreditNote: Story = {
  name: 'Next credit note number',
  render: part(markup, { setup: openDocument(docs.draftCreditNote) }),
};

export const FirstInvoice: Story = {
  name: 'First invoice ever',
  parameters: { docs: { description: { story: 'No previous number: the series starts with a suggested pattern.' } } },
  render: part(markup, {
    session: { company: { ...seller, lastInvoiceReference: undefined } },
    setup: openDocument(docs.draft),
  }),
};

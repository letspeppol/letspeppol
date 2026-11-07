import type { Meta, Story } from '../support/story';
import { InvoicePaymentModal } from '../../src/invoice/edit/components/modals/invoice-payment-modal';
import { docs, draftInvoice, openDocument, seller, withDocument } from '../support/mocks/invoices';
import { modalFrame, part } from './helpers';

const markup = '<invoice-payment-modal invoice-context.bind="invoiceContext" open-modal></invoice-payment-modal>';

const meta: Meta = {
  title: 'Invoice editor/Modals/Payment',
  component: InvoicePaymentModal,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: modalFrame,
      description: {
        component:
          'Payment method and, for a credit transfer, account holder, IBAN, BIC and the structured communication. ' +
          'The company’s own account is filled in by default; an IBAN is required for a credit transfer, so Apply waits for it. ' +
          'When the company has no IBAN on file yet, a small button offers to store the one just typed.',
      },
    },
  },
};

export default meta;

export const CreditTransfer: Story = {
  name: 'Credit transfer',
  render: part(markup, { setup: openDocument(docs.draft) }),
};

export const MissingIban: Story = {
  name: 'IBAN missing',
  render: part(markup, { setup: openDocument(docs.incompleteDraft) }),
};

export const StoreIban: Story = {
  name: 'Offer to store the IBAN',
  render: part(markup, { session: { company: { ...seller, iban: '' } }, setup: openDocument(docs.draft) }),
};

export const Cash: Story = {
  name: 'Cash',
  render: part(markup, {
    setup: withDocument({ ...draftInvoice, PaymentMeans: { PaymentMeansCode: { value: 10, __name: 'In Cash' } } }),
  }),
};

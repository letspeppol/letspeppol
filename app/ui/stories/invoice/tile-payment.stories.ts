import { fn } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { PaymentInfo } from '../../src/invoice/edit/components/tiles/payment-info';
import { CompanyPermission } from '../../src/services/app/company-permission';
import { docs, draftInvoice, openDocument, seller, withDocument } from '../support/mocks/invoices';
import { part, tile } from './helpers';

const markup = tile('<payment-info read-only.bind="invoiceContext.readOnly" show-payment-modal.bind="showPaymentModal"></payment-info>');

const meta: Meta = {
  title: 'Invoice editor/Tiles/Payment',
  component: PaymentInfo,
  args: { showPaymentModal: fn() },
  parameters: {
    docs: {
      description: {
        component:
          'How the document gets paid: method, account holder, IBAN and the Belgian structured communication, shown as +++123/4567/89012+++. ' +
          'A credit transfer without an IBAN marks the tile red. ' +
          'Once a document is delivered the tile carries its payment status action: mark as paid or unpaid, or, for a received invoice, a SEPA QR code to pay it from a banking app. ' +
          'The action is absent for users without the payment status permission.',
      },
    },
  },
};

export default meta;

export const CreditTransfer: Story = {
  name: 'Credit transfer with structured communication',
  render: part(markup, { setup: openDocument(docs.draft) }),
};

export const MissingIban: Story = {
  name: 'IBAN missing',
  render: part(markup, { setup: openDocument(docs.incompleteDraft) }),
};

export const NoMethod: Story = {
  name: 'No payment method',
  render: part(markup, { setup: withDocument({ ...draftInvoice, PaymentMeans: undefined }) }),
};

export const AwaitingPayment: Story = {
  name: 'Sent, awaiting payment',
  render: part(markup, { setup: openDocument(docs.sent) }),
};

export const Paid: Story = {
  name: 'Marked as paid',
  render: part(markup, { setup: openDocument(docs.paid) }),
};

export const PayWithQr: Story = {
  name: 'Received, pay with QR code',
  render: part(markup, { setup: openDocument(docs.received) }),
};

export const NoStatusPermission: Story = {
  name: 'Without the payment status permission',
  render: part(markup, {
    session: { company: seller, admin: false, permissionMask: CompanyPermission.INVOICE_READ },
    setup: openDocument(docs.sent),
  }),
};

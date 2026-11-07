import { fn } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { InvoicePaymentQrModal } from '../../src/invoice/edit/components/modals/invoice-payment-qr-modal';
import { docs, openDocument } from '../support/mocks/invoices';
import { part } from './helpers';

const markup = '<invoice-payment-qr-modal open.bind="true" invoice-context.bind="invoiceContext" close.bind="close" mark-paid.bind="markPaid"></invoice-payment-qr-modal>';

const meta: Meta = {
  title: 'Invoice editor/Modals/Payment QR code',
  component: InvoicePaymentQrModal,
  args: { close: fn(), markPaid: fn() },
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 640 },
      description: {
        component:
          'Opened from the payment tile of a received, unpaid invoice: the transfer details beside a SEPA (EPC) QR code that any Belgian banking app can scan. ' +
          'A structured communication is shown in its +++ form and passed as creditor reference; otherwise the invoice number is used as free text. ' +
          'After paying, the primary button marks the invoice as paid in one step.',
      },
    },
  },
};

export default meta;

export const StructuredReference: Story = {
  name: 'With structured communication',
  render: part(markup, { setup: openDocument(docs.received) }),
};

export const FreeReference: Story = {
  name: 'Invoice number as reference',
  render: part(markup, { setup: openDocument(docs.receivedOverdue) }),
};

export const Phone: Story = {
  name: 'Phone bottom sheet',
  render: part(markup, { setup: openDocument(docs.received) }),
  globals: { viewport: { value: 'phone', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 844 } } },
};

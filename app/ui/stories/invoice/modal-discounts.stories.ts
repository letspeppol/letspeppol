import type { Meta, Story } from '../support/story';
import { InvoiceAllowanceChargeModal } from '../../src/invoice/edit/components/modals/invoice-allowance-charge-modal';
import { adjustedInvoice, scheduledInvoice, sentInvoice, withDocument } from '../support/mocks/invoices';
import { modalFrame, part } from './helpers';

const meta: Meta = {
  title: 'Invoice editor/Modals/Discounts and costs',
  component: InvoiceAllowanceChargeModal,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: modalFrame,
      description: {
        component:
          'Adds discounts and extra costs, either to the whole document (with their own VAT rate) or to one line. ' +
          'Two switches choose discount or cost, and percentage or fixed amount; Add puts the entry in the list below, and Apply recalculates the totals. ' +
          'Remove all is the one destructive action and sits apart on the left.',
      },
    },
  },
};

export default meta;

export const Document: Story = {
  name: 'Whole document',
  render: part('<invoice-allowance-charge-modal open-modal.bind="[invoiceContext.selectedInvoice]"></invoice-allowance-charge-modal>', {
    setup: withDocument(adjustedInvoice),
  }),
};

export const Empty: Story = {
  name: 'Whole document, nothing added yet',
  render: part('<invoice-allowance-charge-modal open-modal.bind="[invoiceContext.selectedInvoice]"></invoice-allowance-charge-modal>', {
    setup: withDocument(scheduledInvoice),
  }),
};

export const Line: Story = {
  name: 'One line',
  parameters: { docs: { description: { story: 'Line discounts take the line’s own VAT rate, so there is no VAT field.' } } },
  render: part('<invoice-allowance-charge-modal open-modal.bind="[invoiceContext.lines[0]]"></invoice-allowance-charge-modal>', {
    setup: withDocument(sentInvoice),
  }),
};

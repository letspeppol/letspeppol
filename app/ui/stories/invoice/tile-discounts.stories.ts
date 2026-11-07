import { fn } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { AllowanceChargeInfo } from '../../src/invoice/edit/components/tiles/allowance-charge-info';
import { adjustedInvoice, scheduledInvoice, withDocument } from '../support/mocks/invoices';
import { part, tile } from './helpers';

const markup = tile('<allowance-charge-info read-only.bind="invoiceContext.readOnly" show-allowance-charge-modal.bind="showAllowanceChargeModal"></allowance-charge-info>');

const meta: Meta = {
  title: 'Invoice editor/Tiles/Discounts and costs',
  component: AllowanceChargeInfo,
  args: { showAllowanceChargeModal: fn() },
  parameters: {
    docs: {
      description: {
        component:
          'Discounts and extra costs on the whole document, as opposed to per line. ' +
          'Each entry shows its description (or Discount / Cost when there is none), a sign, and either the percentage with the resulting amount or a fixed amount. ' +
          'In the editor this tile carries the New badge while the feature is new.',
      },
    },
  },
};

export default meta;

export const Empty: Story = {
  name: 'Nothing added',
  render: part(markup, { setup: withDocument(scheduledInvoice) }),
};

export const DiscountsAndCosts: Story = {
  name: 'Discounts and a cost',
  render: part(markup, { setup: withDocument(adjustedInvoice) }),
};

export const ReadOnly: Story = {
  name: 'Read-only',
  render: part(markup, {
    setup: (context) => {
      withDocument(adjustedInvoice)(context);
      context.readOnly = true;
    },
  }),
};

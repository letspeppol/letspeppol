import { fn, userEvent } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { CustomerSearch } from '../../src/invoice/edit/components/customer-search';
import { clickWhenReady, part, ready } from './helpers';

const markup = `
  <div style="max-width: 28rem">
    <customer-search select-customer-function.bind="selectCustomer" confirm-customer-function.bind="confirmCustomer"></customer-search>
  </div>
`;

const meta: Meta = {
  title: 'Invoice editor/Customer search',
  component: CustomerSearch,
  args: { selectCustomer: fn(), confirmCustomer: fn() },
  render: part(markup),
  parameters: {
    docs: {
      story: { inline: false, iframeHeight: 420 },
      description: {
        component:
          'The first field in the customer dialog: search saved customers by name or VAT number, or open the list with the chevron. ' +
          'Each suggestion shows the name, the VAT number and the address so two similar names can be told apart. ' +
          'Suppliers are left out; only partners marked as customer appear.',
      },
    },
  },
};

export default meta;

export const Idle: Story = {
  name: 'Empty',
};

export const Typing: Story = {
  name: 'Matches while typing',
  play: async ({ canvasElement }) => {
    const input = await ready<HTMLInputElement>(canvasElement, '#customerSearch');
    await new Promise((done) => setTimeout(done, 200));
    await userEvent.type(input, 'an');
    await ready(canvasElement, '#customer-search-results');
  },
};

export const AllPartners: Story = {
  name: 'All saved customers',
  play: async ({ canvasElement }) => {
    await new Promise((done) => setTimeout(done, 200));
    await clickWhenReady(canvasElement, '.customer-search .icon-btn');
    await ready(canvasElement, '#customer-search-results');
  },
};

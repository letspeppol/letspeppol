import { fn } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { CustomerInfo } from '../../src/invoice/edit/components/tiles/customer-info';
import { docs, openDocument, startNewDocument } from '../support/mocks/invoices';
import { part, tile } from './helpers';

const markup = (width?: string) => tile(
  '<customer-info read-only.bind="invoiceContext.readOnly" party.bind="invoiceContext.partnerParty" show-customer-modal.bind="showCustomerModal"></customer-info>',
  width,
);

const meta: Meta = {
  title: 'Invoice editor/Tiles/Customer',
  component: CustomerInfo,
  args: { showCustomerModal: fn() },
  parameters: {
    docs: {
      description: {
        component:
          'The customer tile names who the document is for; on received and self-billed documents the same tile is titled Supplier. ' +
          'The whole header opens the customer dialog while the document is editable, and the pencil is the visible cue. ' +
          'Without a legal name the tile gets the red missing-required border, the one error signal tiles use. ' +
          'Long company names wrap; from 22rem wide the tile switches to label-left rows through a container query.',
      },
    },
  },
};

export default meta;

export const Filled: Story = {
  name: 'Customer chosen',
  render: part(markup(), { setup: openDocument(docs.draft) }),
};

export const Missing: Story = {
  name: 'No customer yet',
  parameters: { docs: { description: { story: 'A new invoice before a customer is picked.' } } },
  render: part(markup(), { setup: startNewDocument() }),
};

export const LongName: Story = {
  name: 'Long company name',
  render: part(markup(), { setup: openDocument(docs.sent) }),
};

export const Supplier: Story = {
  name: 'Supplier on a received invoice',
  parameters: { docs: { description: { story: 'Read-only: no pencil and no header action.' } } },
  render: part(markup(), { setup: openDocument(docs.received) }),
};

export const Wide: Story = {
  name: 'Wide tile',
  parameters: { docs: { description: { story: 'At 22rem and wider labels sit left of their values.' } } },
  render: part(markup('30rem'), { setup: openDocument(docs.draft) }),
};

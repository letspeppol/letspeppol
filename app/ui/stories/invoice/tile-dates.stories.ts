import { fn } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { DateInfo } from '../../src/invoice/edit/components/tiles/date-info';
import { docs, intraCommunityDraft, openDocument, withDocument } from '../support/mocks/invoices';
import { part, tile } from './helpers';

const markup = tile('<date-info read-only.bind="invoiceContext.readOnly" show-date-modal.bind="showDateModal"></date-info>');

const meta: Meta = {
  title: 'Invoice editor/Tiles/Dates',
  component: DateInfo,
  args: { showDateModal: fn() },
  parameters: {
    docs: {
      description: {
        component:
          'Issue date, payment terms and due date in one tile; dates use the D/M/YYYY format and tabular figures. ' +
          'Credit notes have no due date. Lines exempt for intra-community supply or export also need a delivery date and country, which then appear here. ' +
          'A due date in the past or missing delivery details on an editable document mark the tile red.',
      },
    },
  },
};

export default meta;

export const Terms: Story = {
  name: 'Invoice with payment terms',
  render: part(markup, { setup: openDocument(docs.sent) }),
};

export const CreditNote: Story = {
  name: 'Credit note',
  render: part(markup, { setup: openDocument(docs.creditNote) }),
};

export const DueInPast: Story = {
  name: 'Due date in the past',
  render: part(markup, { setup: openDocument(docs.incompleteDraft) }),
};

export const Delivery: Story = {
  name: 'With delivery date and country',
  render: part(markup, { setup: openDocument(docs.intraCommunityDraft) }),
};

export const DeliveryMissing: Story = {
  name: 'Delivery details missing',
  parameters: { docs: { description: { story: 'An intra-community line without delivery details blocks sending.' } } },
  render: part(markup, { setup: withDocument({ ...intraCommunityDraft, Delivery: undefined }) }),
};

import { userEvent } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { InvoiceZeroVatReasonModal } from '../../src/invoice/edit/components/modals/invoice-zero-vat-reason-modal';
import { InvoiceEditItems } from '../../src/invoice/edit/components/invoice-edit-items';
import { docs, openDocument, sendingInvoice, withDocument } from '../support/mocks/invoices';
import { clickWhenReady, part, ready } from './helpers';

const markup = '<invoice-edit-items read-only.bind="false" auto-save.bind="autoSave"></invoice-edit-items>';

const meta: Meta = {
  title: 'Invoice editor/Modals/0% VAT reason',
  component: InvoiceZeroVatReasonModal,
  args: { autoSave: () => undefined },
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 560 },
      description: {
        component:
          'Opens when a line is set to 0% VAT: Peppol needs the exemption category (exempt, intra-community supply, export, reverse charge or zero rated) and an explanation. ' +
          'Both are required; Cancel puts the previous rate back. ' +
          'An explanation already used on another line of the same document is suggested. Shown here opened from the lines, the way it appears in the editor.',
      },
    },
  },
};

export default meta;

export const NewReason: Story = {
  name: 'Choosing 0% on a line',
  render: part(markup, { components: [InvoiceEditItems], setup: withDocument(sendingInvoice) }),
  play: async ({ canvasElement }) => {
    const select = await ready<HTMLSelectElement>(canvasElement, '.line-table tbody tr:first-child .col-vat select');
    await userEvent.selectOptions(select, '0');
    await ready(canvasElement, '#zero-vat-modal-title');
  },
};

export const ExistingReason: Story = {
  name: 'Editing an intra-community reason',
  render: part(markup, { components: [InvoiceEditItems], setup: openDocument(docs.intraCommunityDraft) }),
  play: async ({ canvasElement }) => {
    await clickWhenReady(canvasElement, '.line-table tbody tr:first-child .vat-cell .icon-btn');
    await ready(canvasElement, '#zero-vat-modal-title');
  },
};

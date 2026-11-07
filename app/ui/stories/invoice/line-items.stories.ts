import '../../src/invoice/edit/components/customer-search.css';
import '../../src/invoice/edit/components/tiles/allowance-charge-info.css';
import { fn } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { InvoiceEditItems } from '../../src/invoice/edit/components/invoice-edit-items';
import { InvoiceAllowanceChargeModal } from '../../src/invoice/edit/components/modals/invoice-allowance-charge-modal';
import { createNotSubjectToVatCategory } from '../../src/services/app/vat-rules';
import {
  composeInvoice,
  docs,
  draftInvoice,
  openDocument,
  partyOf,
  seller,
  sendingInvoice,
  sentInvoice,
  withDocument,
} from '../support/mocks/invoices';
import { part } from './helpers';

const markup = `
  <invoice-allowance-charge-modal component.ref="allowanceChargeModal"></invoice-allowance-charge-modal>
  <invoice-edit-items read-only.bind="invoiceContext.readOnly" auto-save.bind="autoSave" invoice-allowance-charge-modal.bind="allowanceChargeModal"></invoice-edit-items>
`;

const components = [InvoiceAllowanceChargeModal];

const notRegisteredSeller = { ...seller, vatNumber: undefined };

const notRegisteredInvoice = composeInvoice({
  issueDate: sendingInvoice.IssueDate,
  dueDate: sendingInvoice.DueDate,
  customer: partyOf.zwaan,
  orderReference: 'Zwaan wk 45',
  lines: [
    { name: 'Desembrood volkoren', quantity: 14, price: 3.6, category: createNotSubjectToVatCategory() },
    { name: 'Taart van de week (12 personen)', quantity: 2, price: 32, category: createNotSubjectToVatCategory() },
  ],
});

const meta: Meta = {
  title: 'Invoice editor/Line items',
  component: InvoiceEditItems,
  args: { autoSave: fn() },
  parameters: {
    docs: {
      description: {
        component:
          'The lines are the body of the document: number, name with product search, description, quantity × unit price, VAT rate and the line total. ' +
          'Required cells that are still empty get a red asterisk. Choosing 0% VAT asks for an exemption reason, and the reason icon stays next to the rate. ' +
          '“Discounts per line” adds a column for line discounts and costs. Companies without a VAT number see no VAT column and totals include VAT. ' +
          'On tablets the table scrolls sideways with number and name pinned; on phones every line becomes a card.',
      },
    },
  },
};

export default meta;

export const MixedVat: Story = {
  name: 'Several lines, mixed VAT rates',
  render: part(markup, { components, setup: withDocument(sendingInvoice) }),
};

export const LineDiscounts: Story = {
  name: 'Discounts and costs per line',
  parameters: { docs: { description: { story: 'A volume discount as a percentage and a fixed charge; the pencil opens the line discount dialog.' } } },
  render: part(markup, { components, setup: withDocument(draftInvoice) }),
};

export const ZeroVat: Story = {
  name: '0% VAT with an exemption reason',
  render: part(markup, { components, setup: openDocument(docs.intraCommunityDraft) }),
};

export const Incomplete: Story = {
  name: 'Required values missing',
  render: part(markup, { components, setup: openDocument(docs.incompleteDraft) }),
};

export const NotVatRegistered: Story = {
  name: 'Company without a VAT number',
  parameters: { docs: { description: { story: 'No VAT column, and line totals include VAT.' } } },
  render: part(markup, { components, session: { company: notRegisteredSeller }, setup: withDocument(notRegisteredInvoice) }),
};

export const ReadOnly: Story = {
  name: 'Read-only',
  render: part(markup, { components, setup: openDocument(docs.sent) }),
};

export const Empty: Story = {
  name: 'No lines yet',
  render: part(markup, { components, setup: withDocument({ ...sentInvoice, ID: '', InvoiceLine: [] }) }),
};

export const Tablet: Story = {
  name: 'Tablet portrait',
  render: part(markup, { components, setup: withDocument(draftInvoice) }),
  globals: { viewport: { value: 'tabletPortrait', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 520 } } },
};

export const Phone: Story = {
  name: 'Phone',
  render: part(markup, { components, setup: withDocument(draftInvoice) }),
  globals: { viewport: { value: 'phone', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 900 } } },
};

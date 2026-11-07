import { fn } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { InvoiceCustomerModal } from '../../src/invoice/edit/components/modals/invoice-customer-modal';
import { CompanyPermission } from '../../src/services/app/company-permission';
import { docs, openDocument, seller, startNewDocument } from '../support/mocks/invoices';
import { clickWhenReady, part, ready } from './helpers';

const markup = '<invoice-customer-modal invoice-context.bind="invoiceContext" open-modal.bind="[customerSaved]"></invoice-customer-modal>';

const meta: Meta = {
  title: 'Invoice editor/Modals/Customer',
  component: InvoiceCustomerModal,
  args: { customerSaved: fn() },
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 820 },
      description: {
        component:
          'The widest dialog: pick a saved customer at the top, or enter a new one below in two groups, company and address. ' +
          'Entering an enterprise number, VAT number or Belgian Peppol ID looks the company up in the register and fills in what it finds; the Peppol ID is the one required field. ' +
          'Save as partner keeps the new customer for next time and only appears for users who may manage partners.',
      },
    },
  },
};

export default meta;

export const NewCustomer: Story = {
  name: 'New invoice, nobody chosen',
  render: part(markup, { setup: startNewDocument() }),
};

export const Existing: Story = {
  name: 'Customer already filled in',
  render: part(markup, { setup: openDocument(docs.draft) }),
};

export const SavedPartners: Story = {
  name: 'Saved customers open',
  render: part(markup, { setup: startNewDocument() }),
  play: async ({ canvasElement }) => {
    await ready(canvasElement, '#customerSearch');
    await new Promise((done) => setTimeout(done, 300));
    await clickWhenReady(canvasElement, '.customer-search .icon-btn');
    await ready(canvasElement, '#customer-search-results');
  },
};

export const WithoutPartnerPermission: Story = {
  name: 'Without the partner permission',
  render: part(markup, {
    session: { company: seller, admin: false, permissionMask: CompanyPermission.INVOICE_SEND },
    setup: openDocument(docs.draft),
  }),
};

export const Phone: Story = {
  name: 'Phone bottom sheet',
  render: part(markup, { setup: openDocument(docs.draft) }),
  globals: { viewport: { value: 'phone', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 844 } } },
};

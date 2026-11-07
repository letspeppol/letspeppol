import '../../src/invoice/edit/components/customer-search.css';
import '../../src/invoice/edit/components/tiles/allowance-charge-info.css';
import { fn, userEvent } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { ProductNameSearch } from '../../src/invoice/edit/components/product-name-search';
import { InvoiceEditItems } from '../../src/invoice/edit/components/invoice-edit-items';
import { DocumentType } from '../../src/services/app/invoice-service';
import type { Invoice } from '../../src/services/peppol/ubl';
import { invoiceLines, partyOf, startNewDocument } from '../support/mocks/invoices';
import { part, ready } from './helpers';

const markup = '<invoice-edit-items read-only.bind="false" auto-save.bind="autoSave"></invoice-edit-items>';

const setup = startNewDocument(DocumentType.INVOICE, (doc) => {
  doc.AccountingCustomerParty.Party = structuredClone(partyOf.anker);
  (doc as Invoice).InvoiceLine = invoiceLines([
    { name: 'Desembrood volkoren', quantity: 40, price: 3.6, vat: 6 },
    { name: '', quantity: 24, price: 0, vat: 21 },
  ]);
});

const lastName = '.line-table tbody tr:last-child input[name=productSearch]';

async function typeInLastLine(canvasElement: HTMLElement, text: string) {
  const input = await ready<HTMLInputElement>(canvasElement, lastName);
  await new Promise((done) => setTimeout(done, 250));
  await userEvent.type(input, text);
  await ready(canvasElement, '.product-suggestions');
  return input;
}

const meta: Meta = {
  title: 'Invoice editor/Product search',
  component: ProductNameSearch,
  args: { autoSave: fn() },
  render: part(markup, { setup, components: [InvoiceEditItems] }),
  parameters: {
    docs: {
      story: { inline: false, iframeHeight: 420 },
      description: {
        component:
          'The name cell of an invoice line doubles as a product search: typing filters saved products by name and picking one fills in price, description and VAT rate. ' +
          'Up to eight suggestions show name, description and price with stock; arrow keys move the highlight and Enter picks it. ' +
          'Free text is always allowed, so a line never needs a saved product. The field has no look of its own: it takes the style of the line table cell it sits in, so it is shown here inside the lines.',
      },
    },
  },
};

export default meta;

export const NewLine: Story = {
  name: 'Empty name on a new line',
};

export const Suggestions: Story = {
  name: 'Suggestions while typing',
  play: async ({ canvasElement }) => {
    await typeInLastLine(canvasElement, 'croi');
  },
};

export const Picked: Story = {
  name: 'Product picked',
  parameters: { docs: { description: { story: 'Enter picks the highlighted product and fills in the rest of the line.' } } },
  play: async ({ canvasElement }) => {
    const input = await typeInLastLine(canvasElement, 'croi');
    await userEvent.type(input, '{Enter}');
  },
};

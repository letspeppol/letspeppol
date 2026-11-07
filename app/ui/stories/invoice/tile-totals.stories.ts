import type { Meta, Story } from '../support/story';
import { TotalInfo } from '../../src/invoice/edit/components/tiles/total-info';
import { docs, openDocument, sentInvoice, withDocument } from '../support/mocks/invoices';
import { part, tile } from './helpers';

const markup = tile('<total-info read-only.bind="invoiceContext.readOnly" tax-total.bind="invoiceContext.selectedInvoice.TaxTotal[0]"></total-info>', '24rem');

const prepaid = structuredClone(sentInvoice);
prepaid.LegalMonetaryTotal.PrepaidAmount = { __currencyID: 'EUR', value: 250 };
prepaid.LegalMonetaryTotal.PayableAmount = {
  __currencyID: 'EUR',
  value: Math.round((prepaid.LegalMonetaryTotal.TaxInclusiveAmount.value - 250) * 100) / 100,
};

const meta: Meta = {
  title: 'Invoice editor/Tiles/Totals',
  component: TotalInfo,
  parameters: {
    docs: {
      description: {
        component:
          'The totals sit under the lines, right-aligned with tabular figures: subtotal, VAT and the total in weight 700. ' +
          'With more than one VAT rate the VAT line is broken down per rate. ' +
          'A prepaid amount adds a payable amount line; that second bold line is the amount the customer still owes.',
      },
    },
  },
};

export default meta;

export const SingleRate: Story = {
  name: 'One VAT rate',
  render: part(markup, { setup: openDocument(docs.paid) }),
};

export const MixedRates: Story = {
  name: 'Mixed VAT rates',
  render: part(markup, { setup: openDocument(docs.draft) }),
};

export const ZeroVat: Story = {
  name: 'Intra-community at 0% VAT',
  render: part(markup, { setup: openDocument(docs.intraCommunityDraft) }),
};

export const Prepaid: Story = {
  name: 'With a prepaid amount',
  render: part(markup, { setup: withDocument(prepaid) }),
};

export const CreditNote: Story = {
  name: 'Credit note',
  render: part(markup, { setup: openDocument(docs.creditNote) }),
};

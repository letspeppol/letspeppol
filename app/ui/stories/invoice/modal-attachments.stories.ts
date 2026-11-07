import type { Meta, Story } from '../support/story';
import { InvoiceAttachmentModal } from '../../src/invoice/edit/components/modals/invoice-attachment-modal';
import {
  deliveryNotePdf,
  docs,
  draftInvoice,
  generatedPdf,
  openDocument,
  orderSpreadsheet,
  priceListLink,
  withDocument,
} from '../support/mocks/invoices';
import { clickWhenReady, part, ready } from './helpers';

const markup = '<invoice-attachment-modal invoice-context.bind="invoiceContext" open-modal></invoice-attachment-modal>';

const meta: Meta = {
  title: 'Invoice editor/Modals/Attachments',
  component: InvoiceAttachmentModal,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 820 },
      description: {
        component:
          'Add files (PDF, PNG, JPG, CSV or spreadsheet, up to 3 MB each and 10 MB together) by dropping them on the zone or choosing one, and add links. ' +
          'The generated invoice PDF is a checkbox rather than a row; when an uploaded PDF might replace it, a hint says so. ' +
          'Each attachment can get a description. A link without an address blocks Apply.',
      },
    },
  },
};

export default meta;

export const GeneratedOnly: Story = {
  name: 'Only the generated PDF',
  render: part(markup, { setup: openDocument(docs.paid) }),
};

export const Several: Story = {
  name: 'Files and a link',
  render: part(markup, {
    setup: withDocument({ ...draftInvoice, AdditionalDocumentReference: [generatedPdf, deliveryNotePdf, orderSpreadsheet, priceListLink] }),
  }),
};

export const NewLink: Story = {
  name: 'Link without an address',
  render: part(markup, { setup: openDocument(docs.draft) }),
  play: async ({ canvasElement }) => {
    await clickWhenReady(canvasElement, '.add-link');
    await ready(canvasElement, '.attachment-row input[type=url]');
  },
};

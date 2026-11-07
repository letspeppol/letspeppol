import { fn } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { AttachmentInfo } from '../../src/invoice/edit/components/tiles/attachment-info';
import {
  deliveryNotePdf,
  docs,
  draftInvoice,
  generatedPdf,
  openDocument,
  orderSpreadsheet,
  photoAttachment,
  priceListLink,
  scheduledInvoice,
  withDocument,
} from '../support/mocks/invoices';
import { part, tile } from './helpers';

const markup = tile('<attachment-info read-only.bind="invoiceContext.readOnly" show-attachment-modal.bind="showAttachmentModal"></attachment-info>');

const meta: Meta = {
  title: 'Invoice editor/Tiles/Attachments',
  component: AttachmentInfo,
  args: { showAttachmentModal: fn() },
  parameters: {
    docs: {
      description: {
        component:
          'Files and links that travel with the document. Each row is a button that downloads the file or opens the link; the file type icon comes first and long names are shortened in the middle so the extension stays visible. ' +
          'The generated invoice PDF is listed like any other attachment until the document is sent. ' +
          'Received documents keep the supplier’s own PDF here.',
      },
    },
  },
};

export default meta;

export const GeneratedOnly: Story = {
  name: 'Only the generated PDF',
  render: part(markup, { setup: withDocument(scheduledInvoice) }),
};

export const Mixed: Story = {
  name: 'Files, links and a long file name',
  render: part(markup, {
    setup: withDocument({
      ...draftInvoice,
      AdditionalDocumentReference: [generatedPdf, deliveryNotePdf, orderSpreadsheet, photoAttachment, priceListLink],
    }),
  }),
};

export const Empty: Story = {
  name: 'No attachments',
  render: part(markup, { setup: withDocument({ ...draftInvoice, AdditionalDocumentReference: [] }) }),
};

export const Received: Story = {
  name: 'Received with the supplier’s PDF',
  render: part(markup, { setup: openDocument(docs.received) }),
};

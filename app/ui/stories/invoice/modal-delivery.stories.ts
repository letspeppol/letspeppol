import type { Meta, Story } from '../support/story';
import { InvoiceDeliveryModal } from '../../src/invoice/edit/components/modals/invoice-delivery-modal';
import { deliveryDetails, docs, openDocument, rejectedDeliveryDetails } from '../support/mocks/invoices';
import { modalFrame, part } from './helpers';

const markup = '<invoice-delivery-modal open-modal.bind="[details]"></invoice-delivery-modal>';

const meta: Meta = {
  title: 'Invoice editor/Modals/Delivery proof',
  component: InvoiceDeliveryModal,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: modalFrame,
      description: {
        component:
          'The proof that a document reached the other side, laid out like a stamped receipt: sender and receiver Peppol IDs, the number, and the IDs each access point gave the message. ' +
          'A delivered document gets a green stamp with the receiving access point and time; a rejected one gets a red stamp with the reason. ' +
          'Colour is never the only signal: each stamp has an icon and a label.',
      },
    },
  },
};

export default meta;

export const Delivered: Story = {
  name: 'Delivered',
  render: part(markup, { setup: openDocument(docs.sent), props: { details: deliveryDetails } }),
};

export const Rejected: Story = {
  name: 'Rejected by the receiver',
  render: part(markup, { setup: openDocument(docs.errored), props: { details: rejectedDeliveryDetails } }),
};

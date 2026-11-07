import { userEvent } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { Invoices } from '../../src/invoice/invoices';
import { DocumentDirection } from '../../src/services/app/invoice-service';
import { CompanyPermission } from '../../src/services/app/company-permission';
import { archiveDocuments, invoiceMocks, loadingDocuments, seller, type InvoiceStoryOptions } from '../support/mocks/invoices';
import { clickWhenReady, ready } from './helpers';

function screen(options: InvoiceStoryOptions = {}) {
  return () => ({ items: invoiceMocks(options) });
}

const meta: Meta = {
  title: 'Screens/Invoices/Overview',
  component: Invoices,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 900 },
      description: {
        component:
          'The invoice list is where people come back to: what went out, what came in, what is still open. ' +
          'The segmented row switches between all, outgoing, incoming and drafts (drafts carry a count); the filters narrow by type, payment status, number and partner. ' +
          'Each row carries one status pill in the semantic colours (paid green, open and scheduled amber, overdue and error red, sent and received navy) plus a small payment dot, so colour is never the only signal. ' +
          'Row actions are icon buttons that only appear when the user has the permission and the action makes sense; on phones the table becomes row cards with the partner as title.',
      },
    },
  },
};

export default meta;

export const AllDocuments: Story = {
  name: 'All documents, mixed statuses',
  parameters: { docs: { description: { story: 'Every status the list can show: scheduled, sending, sent, paid, overdue, error (fresh and acknowledged), received, uploaded and self-billed.' } } },
  render: screen(),
};

export const Outgoing: Story = {
  name: 'Outgoing',
  parameters: { docs: { description: { story: 'The partner column is called Customer and the direction subline disappears.' } } },
  render: screen({ setup: (context) => { context.activeBox = DocumentDirection.OUTGOING; } }),
};

export const Incoming: Story = {
  name: 'Incoming',
  parameters: { docs: { description: { story: 'Received documents: the partner column becomes Supplier and only status actions remain.' } } },
  render: screen({ setup: (context) => { context.activeBox = DocumentDirection.INCOMING; } }),
};

export const Drafts: Story = {
  name: 'Drafts',
  parameters: { docs: { description: { story: 'Drafts have no number yet and offer delete; the badge on the segment counts them.' } } },
  render: screen({ setup: (context) => { context.activeBox = 'DRAFTS'; } }),
};

export const OverdueFilter: Story = {
  name: 'Filtered on overdue',
  parameters: { docs: { description: { story: 'Reached from the dashboard tile: the status filter is pre-set and the list shows only overdue documents.' } } },
  render: screen({ setup: (context) => context.requestStatusFilter('overdue') }),
};

export const NoMatches: Story = {
  name: 'Filters without matches',
  parameters: { docs: { description: { story: 'An empty result under filters offers one way out: clear the filters.' } } },
  render: screen(),
  play: async ({ canvasElement }) => {
    await ready(canvasElement, '.invoice-table tbody tr');
    await userEvent.type(canvasElement.querySelectorAll<HTMLInputElement>('.invoice-filters input[type=search]')[1], 'Delhaize');
    await ready(canvasElement, '.empty-state .btn');
  },
};

export const Paging: Story = {
  name: 'Long list with pages',
  parameters: { docs: { description: { story: 'Twenty rows per page; the pager shows the range and disables what is not possible.' } } },
  render: screen({ documents: archiveDocuments }),
};

export const SecondPage: Story = {
  name: 'Second page',
  render: screen({ documents: archiveDocuments }),
  play: async ({ canvasElement }) => {
    await ready(canvasElement, '.invoice-table tbody tr');
    await clickWhenReady(canvasElement, '.invoice-pager .btn:last-child:not([disabled])');
  },
};

export const Loading: Story = {
  name: 'Loading',
  parameters: { docs: { description: { story: 'First load: a thin bar on the table and a spinner with a status line where the rows will be.' } } },
  render: screen({ getDocuments: loadingDocuments }),
};

export const Empty: Story = {
  name: 'No invoices yet',
  parameters: { docs: { description: { story: 'A new company: one line that explains what will appear and one button to start.' } } },
  render: screen({ documents: [] }),
};

export const EmptyIncoming: Story = {
  name: 'Nothing received yet',
  render: screen({ documents: [], setup: (context) => { context.activeBox = DocumentDirection.INCOMING; } }),
};

export const ReadOnly: Story = {
  name: 'Read-only user',
  parameters: { docs: { description: { story: 'A user with only the read permission: no upload, no new document, no draft or payment actions on the rows.' } } },
  render: screen({ session: { company: seller, admin: false, permissionMask: CompanyPermission.INVOICE_READ } }),
};

export const Tablet: Story = {
  name: 'Tablet portrait',
  parameters: { docs: { description: { story: 'Between 768 and 1023px the table stays a table; the payment dot and issue date columns step aside so status and actions stay in view.' } } },
  render: screen(),
  globals: { viewport: { value: 'tabletPortrait', isRotated: false } },
};

export const Phone: Story = {
  name: 'Phone',
  parameters: { docs: { description: { story: 'Rows become cards with the partner as title, labelled fields and the actions as a row of touch targets.' } } },
  render: screen(),
  globals: { viewport: { value: 'phone', isRotated: false } },
};

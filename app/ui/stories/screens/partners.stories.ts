import { fireEvent } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { Partners } from '../../src/partner/partners';
import type { PartnerDto } from '../../src/services/app/partner-service';
import { CompanyPermission } from '../../src/services/app/company-permission';
import { sessionMocks, type SessionOptions } from '../support/mocks/session';
import { findElement, manyPartners, partnerServices, shellServices } from '../support/mocks/business';
import { atelier } from '../support/mocks/fixtures';
import { pending, resolved } from '../support/mocks/stub';

function screen(list?: () => Promise<PartnerDto[]>, session: SessionOptions = {}) {
  return () => ({ items: [...sessionMocks(session), ...shellServices(), ...partnerServices(list)] });
}

const readOnly: SessionOptions = {
  company: atelier,
  admin: false,
  permissionMask: CompanyPermission.INVOICE_READ | CompanyPermission.INVOICE_DRAFT,
};

const meta: Meta = {
  title: 'Screens/Partners/Overview',
  component: Partners,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 820 },
      description: {
        component:
          'The partner list is the address book behind every invoice: customers and suppliers in one table, with a segmented filter that carries the counts and a name search. ' +
          'A partner can be both, so the type column shows one pill per role. ' +
          'Sorting is on the column heads; the Peppol ID column is the first to go on tablets, and on phones each row becomes a card with the name as its title. ' +
          'Without the partner permission the list is read-only: no New partner button and no delete column, never disabled controls.',
      },
    },
  },
};

export default meta;

export const Default: Story = {
  name: 'Customers and suppliers',
  render: screen(),
};

export const CustomersFilter: Story = {
  name: 'Filtered on customers',
  parameters: { docs: { description: { story: 'The active segment uses the selected style; counts stay visible on every segment.' } } },
  render: screen(),
  play: async ({ canvasElement }) => {
    await findElement(canvasElement, '.partner-row');
    fireEvent.click(await findElement(canvasElement, '.partner-toolbar .segment', 1));
  },
};

export const NoSearchMatch: Story = {
  name: 'Search without matches',
  render: screen(),
  play: async ({ canvasElement }) => {
    await findElement(canvasElement, '.partner-row');
    fireEvent.input(await findElement(canvasElement, '.partner-search input'), { target: { value: 'Delhaize' } });
    await findElement(canvasElement, '.partner-empty');
  },
};

export const LongList: Story = {
  name: 'Long list with pages',
  parameters: { docs: { description: { story: 'Twenty partners per page; the pager only appears when there is more than one page.' } } },
  render: screen(resolved(manyPartners)),
};

export const Empty: Story = {
  name: 'No partners yet',
  parameters: { docs: { description: { story: 'The first visit invites one action with one line and one button.' } } },
  render: screen(resolved([])),
};

export const Loading: Story = {
  name: 'Loading',
  parameters: { docs: { description: { story: 'There is no separate loading state: until the list arrives the screen shows the first-visit invitation.' } } },
  render: screen(pending()),
};

export const ReadOnly: Story = {
  name: 'Read-only user',
  parameters: { docs: { description: { story: 'A colleague who may draft invoices but not manage partners sees the list without New partner or delete.' } } },
  render: screen(undefined, readOnly),
};

export const DeleteConfirmation: Story = {
  name: 'Delete confirmation',
  parameters: { docs: { description: { story: 'Deleting asks once, names the partner and uses the destructive button style.' } } },
  render: screen(),
  play: async ({ canvasElement }) => {
    fireEvent.click(await findElement(canvasElement, '.partner-row .actions-col .icon-btn', 1));
  },
};

export const Tablet: Story = {
  name: 'Tablet portrait',
  globals: { viewport: { value: 'tabletPortrait', isRotated: false } },
  render: screen(),
};

export const Phone: Story = {
  name: 'Phone, stacked cards',
  globals: { viewport: { value: 'phone', isRotated: false } },
  render: screen(),
};

export const PhoneReadOnly: Story = {
  name: 'Phone, read-only',
  globals: { viewport: { value: 'phone', isRotated: false } },
  render: screen(undefined, readOnly),
};

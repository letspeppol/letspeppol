import { userEvent } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { Heading } from '../../src/components/heading/heading';
import { sessionMocks } from '../support/mocks/session';
import { atelier, BAKERY_PEPPOL_ID, ownerships } from '../support/mocks/fixtures';
import { CompanyPermission } from '../../src/services/app/company-permission';

const meta: Meta = {
  title: 'Components/App band',
  component: Heading,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 140 },
      description: {
        component:
          'The navy band is the institution: the one strong element on every screen. The active section carries the orange marker. ' +
          'On desktop the band measures itself and drops nav icons first, then folds Downloads, Settings and Account into the account menu so it never wraps. ' +
          'On phones it becomes a compact band plus a bottom bar with at most five slots.',
      },
    },
  },
};

export default meta;

export const TwoCompaniesAdmin: Story = {
  name: 'Admin with two companies',
  render: () => ({ items: sessionMocks() }),
};

export const SingleCompany: Story = {
  name: 'One company',
  render: () => ({ items: sessionMocks({ ownerships: ownerships.slice(0, 1) }) }),
};

export const ReadOnlyUser: Story = {
  name: 'User with read-only invoices',
  render: () => ({
    items: sessionMocks({
      company: atelier,
      admin: false,
      permissionMask: CompanyPermission.INVOICE_READ,
    }),
  }),
};

export const WithoutCompany: Story = {
  name: 'Signed in without a company',
  render: () => ({ items: sessionMocks({ company: null }) }),
};

export const Tablet: Story = {
  name: 'Tablet portrait',
  render: () => ({ items: sessionMocks() }),
  globals: { viewport: { value: 'tabletPortrait', isRotated: false } },
};

export const Phone: Story = {
  name: 'Phone with bottom bar',
  render: () => ({ items: sessionMocks() }),
  globals: { viewport: { value: 'phone', isRotated: false } },
};

const clickOn = (selector: string) => async ({ canvasElement }: { canvasElement: HTMLElement }) => {
  const trigger = canvasElement.querySelector<HTMLElement>(selector);
  if (trigger) {
    await userEvent.click(trigger);
  }
};

function visiting(path: string) {
  let original = '';
  const restore = () => {
    if (original) {
      history.replaceState(history.state, '', original);
      original = '';
    }
  };
  return {
    beforeEach: () => {
      original = `${location.pathname}${location.search}${location.hash}`;
      history.replaceState(history.state, '', `${path}${location.search}${location.hash}`);
      return restore;
    },
    play: async () => restore(),
  };
}

const invoicesPath = `/${BAKERY_PEPPOL_ID}/invoices`;

export const AccountMenu: Story = {
  name: 'Account menu open',
  globals: { viewport: { value: 'tabletLandscape', isRotated: false } },
  parameters: {
    docs: {
      story: { inline: false, iframeHeight: 640 },
      description: { story: 'At 1180 px the band no longer fits, so Downloads, Settings, Account and the utilities fold into a popover under the account button.' },
    },
  },
  render: () => ({ items: sessionMocks() }),
  play: clickOn('.account-button'),
};

export const PhoneMoreSheet: Story = {
  name: 'Phone, More sheet open',
  globals: { viewport: { value: 'phone', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 844 } } },
  render: () => ({ items: sessionMocks() }),
  play: clickOn('.bottom-bar button.bar-item'),
};

export const PhoneLandscapeDrawer: Story = {
  name: 'Phone landscape, drawer open',
  globals: { viewport: { value: 'phoneLandscape', isRotated: false } },
  parameters: {
    docs: {
      story: { inline: false, iframeHeight: 390 },
      description: { story: 'Without room for a bottom bar, the menu button opens a left drawer with every section.' },
    },
  },
  render: () => ({ items: sessionMocks() }),
  play: clickOn('.menu-button'),
};

const activeInvoices = visiting(invoicesPath);

export const ActiveSection: Story = {
  name: 'Active section',
  parameters: {
    docs: { description: { story: 'The orange marker follows the section in the address bar; here the page is the invoice list.' } },
  },
  beforeEach: activeInvoices.beforeEach,
  render: () => ({ items: sessionMocks({ path: invoicesPath }) }),
  play: activeInvoices.play,
};

const activeInvoicesPhone = visiting(invoicesPath);

export const PhoneActiveSection: Story = {
  name: 'Phone, active section',
  globals: { viewport: { value: 'phone', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 844 } } },
  beforeEach: activeInvoicesPhone.beforeEach,
  render: () => ({ items: sessionMocks({ path: invoicesPath }) }),
  play: activeInvoicesPhone.play,
};

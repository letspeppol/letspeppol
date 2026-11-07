import type { Meta, Story } from '../support/story';
import { Dashboard } from '../../src/dashboard/dashboard';
import { sessionMocks, type SessionOptions } from '../support/mocks/session';
import { emptyTotals, shellServices, totalsWithProblems, type ShellOptions } from '../support/mocks/business';
import { pending, resolved } from '../support/mocks/stub';
import { atelier, bakery, ownerships } from '../support/mocks/fixtures';
import { CompanyPermission } from '../../src/services/app/company-permission';

function screen(session: SessionOptions = {}, shell: ShellOptions = {}) {
  return () => ({ items: [...sessionMocks(session), ...shellServices(shell)] });
}

const meta: Meta = {
  title: 'Screens/Dashboard',
  component: Dashboard,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 720 },
      description: {
        component:
          'The dashboard answers one question for someone coming back: what came in, what went out, and what is late. ' +
          'Two ledger sheets show this year’s total in display weight with open and overdue below it; overdue turns red only when it is above zero. ' +
          'The tag next to the title says whether amounts include VAT, which follows the company’s VAT number and the user’s preference. ' +
          'A red banner sits above the ledgers only when invoices failed to deliver, and people without invoice access get one plain line instead of the figures.',
      },
    },
  },
};

export default meta;

export const Typical: Story = {
  name: 'Typical month',
  render: screen(),
};

export const DeliveryProblems: Story = {
  name: 'Undelivered invoices',
  parameters: { docs: { description: { story: 'Two invoices bounced on the Peppol network; the banner is the one place this screen uses the danger colour as a surface.' } } },
  render: screen({}, { totals: resolved(totalsWithProblems) }),
};

export const NewCompany: Story = {
  name: 'Brand-new company',
  parameters: { docs: { description: { story: 'A company that has not sent or received anything yet sees zeroes; there is no separate empty state.' } } },
  render: screen({}, { totals: resolved(emptyTotals) }),
};

export const Loading: Story = {
  name: 'Loading',
  parameters: { docs: { description: { story: 'While the totals are on their way the ledgers already show zeroes, which reads the same as a brand-new company.' } } },
  render: screen({}, { totals: pending() }),
};

export const WithoutVatNumber: Story = {
  name: 'Company without VAT number',
  parameters: { docs: { description: { story: 'Without a VAT number the figures are always shown including VAT and the tag says so.' } } },
  render: screen({ company: { ...bakery, vatNumber: '' } }),
};

export const NoInvoiceAccess: Story = {
  name: 'User without invoice access',
  parameters: { docs: { description: { story: 'A colleague who may only manage partners and products sees no figures at all.' } } },
  render: screen({
    company: atelier,
    admin: false,
    permissionMask: CompanyPermission.PARTNER_MANAGE | CompanyPermission.PRODUCT_MANAGE,
  }),
};

export const NotOnPeppol: Story = {
  name: 'Company not active on Peppol',
  parameters: { docs: { description: { story: 'When the company is registered with another access point, every screen opens with this notice; only admins get the button to fix it.' } } },
  render: screen({
    company: { ...bakery, peppolActive: false },
    ownerships: [{ ...ownerships[0], peppolActive: false }, ownerships[1]],
  }),
};

export const Tablet: Story = {
  name: 'Tablet portrait',
  globals: { viewport: { value: 'tabletPortrait', isRotated: false } },
  render: screen(),
};

export const Phone: Story = {
  name: 'Phone',
  globals: { viewport: { value: 'phone', isRotated: false } },
  render: screen({}, { totals: resolved(totalsWithProblems) }),
};

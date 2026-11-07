import { userEvent } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { CompanySettings } from '../../src/settings/company-settings';
import { CompanyPermission } from '../../src/services/app/company-permission';
import { sessionMocks, type SessionOptions } from '../support/mocks/session';
import { atelier as atelierFixture, bakery as bakeryFixture, ownerships } from '../support/mocks/fixtures';
import {
  companySettingsService,
  donationMocks,
  element,
  failure,
  pageTemplate,
  registrationService,
  visibleElement,
  type CompanySettingsOptions,
} from '../support/mocks/accounts';

const bakery = { ...bakeryFixture, paymentTerms: 'NET_30' };
const atelier = { ...atelierFixture, paymentTerms: 'EOM_30' };

interface SettingsPageOptions extends CompanySettingsOptions {
  session?: SessionOptions;
  registerCompany?: () => Promise<boolean>;
}

function settingsPage(options: SettingsPageOptions = {}) {
  const company = options.company ?? bakery;
  return () => ({
    template: pageTemplate('company-settings'),
    items: [
      companySettingsService({ ...options, company }),
      registrationService({ registerCompany: options.registerCompany }),
      ...donationMocks(),
      ...sessionMocks({ company, path: `/${company.peppolId}/settings`, ...options.session }),
    ],
  });
}

const inactiveBakery = { ...bakery, peppolActive: false };
const inactiveOwnerships = [{ ...ownerships[0], peppolActive: false }, ownerships[1]];

async function confirmSending(canvasElement: HTMLElement) {
  await userEvent.click(await element(canvasElement, '.settings-peppol-options > label.checkbox input'));
  return visibleElement(canvasElement, '.confirmation-card');
}

const meta: Meta = {
  title: 'Screens/Settings',
  component: CompanySettings,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 900 },
      description: {
        component:
          'Company settings: legal details from the enterprise register (read-only), the Peppol connection, address, payment details, VAT ruleset and notifications. ' +
          'Administrators see the Company/Users tabs; everyone else sees the page without tabs. Without the “Company settings” permission every field is shown but disabled and the save bar disappears, so people can still look things up. ' +
          'Turning Peppol sending or receiving on or off always goes through a warning dialog, and failures are explained inline in the Peppol sheet, not only in a toast. ' +
          'Under 1024px the save and discard buttons move into the sticky bottom action bar.',
      },
    },
  },
};

export default meta;

export const Administrator: Story = {
  name: 'Administrator editing',
  render: settingsPage(),
  parameters: {
    docs: { description: { story: 'An administrator of an active company on the Recommand access point: everything editable, receiving on.' } },
  },
};

export const ReadOnly: Story = {
  name: 'User without company settings permission',
  render: settingsPage({
    company: atelier,
    session: {
      admin: false,
      permissionMask: CompanyPermission.INVOICE_READ | CompanyPermission.INVOICE_DRAFT | CompanyPermission.INVOICE_SEND,
    },
  }),
  parameters: {
    docs: { description: { story: 'A colleague who may send invoices but not change settings: no tabs, no contract download, disabled fields and no save bar.' } },
  },
};

export const VatExempt: Story = {
  name: 'VAT-exempt small enterprise',
  render: settingsPage({ company: { ...bakery, vatRuleset: 'VAT_EXEMPT_ART_56BIS' } }),
  parameters: {
    docs: { description: { story: 'The VAT switch set to the Art. 56bis exemption for small enterprises.' } },
  },
};

export const PeppolNotActive: Story = {
  name: 'Peppol not active, receiving off',
  render: settingsPage({ company: inactiveBakery, session: { ownerships: inactiveOwnerships } }),
  parameters: {
    docs: { description: { story: 'Not registered on Peppol yet: the red banner under the band, no access point, both switches off.' } },
  },
};

export const ConfirmActivation: Story = {
  name: 'Confirm turning Peppol on',
  render: settingsPage({ company: inactiveBakery, session: { ownerships: inactiveOwnerships } }),
  play: async ({ canvasElement }) => {
    await confirmSending(canvasElement);
  },
  parameters: {
    docs: { description: { story: 'The warning dialog that guards switching sending on.' } },
  },
};

export const ActivationRefused: Story = {
  name: 'Activation refused (registered elsewhere)',
  render: settingsPage({
    company: inactiveBakery,
    session: { ownerships: inactiveOwnerships },
    registerCompany: failure(409, 'Scrada'),
  }),
  play: async ({ canvasElement }) => {
    const dialog = await confirmSending(canvasElement);
    await userEvent.click(await element(dialog, '.modal-footer .btn:last-child'));
    await element(canvasElement, '.settings-notice');
  },
  parameters: {
    docs: { description: { story: 'The network refused the registration because another provider already holds this Peppol ID; the notice names it and says what to do.' } },
  },
};

export const ReceivingElsewhere: Story = {
  name: 'Receiving through another provider',
  render: settingsPage({
    settings: { receiveDocuments: false, receivingSmpUrl: 'smp.e-invoice.be' },
  }),
};

export const SettingsUnavailable: Story = {
  name: 'Peppol settings unavailable',
  render: settingsPage({ settings: 'failed' }),
};

export const Tablet: Story = {
  name: 'Tablet portrait',
  render: settingsPage(),
  globals: { viewport: { value: 'tabletPortrait', isRotated: false } },
};

export const Phone: Story = {
  name: 'Phone',
  render: settingsPage(),
  globals: { viewport: { value: 'phone', isRotated: false } },
};

export const PhoneReadOnly: Story = {
  name: 'Phone, read-only',
  render: settingsPage({
    company: atelier,
    session: { admin: false, permissionMask: CompanyPermission.INVOICE_READ },
  }),
  globals: { viewport: { value: 'phone', isRotated: false } },
};

import { userEvent } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { Account } from '../../src/account/account';
import { sessionMocks, type SessionOptions } from '../support/mocks/session';
import { BAKERY_PEPPOL_ID } from '../support/mocks/fixtures';
import {
  accountServices,
  donationMocks,
  element,
  pageTemplate,
  passkeys,
  visibleElement,
  type AccountServicesOptions,
} from '../support/mocks/accounts';

function accountPage(options: AccountServicesOptions = {}, session: SessionOptions = {}) {
  return () => ({
    template: pageTemplate('account'),
    items: [
      ...accountServices(options),
      ...donationMocks(),
      ...sessionMocks({ path: session.company === null ? '/account' : `/${BAKERY_PEPPOL_ID}/account`, ...session }),
    ],
  });
}

async function startTwoFactorSetup(canvasElement: HTMLElement, code: string) {
  await userEvent.click(await element(canvasElement, '.totp-block .btn.primary'));
  await element(canvasElement, '.totp-qr img');
  await userEvent.type(await element(canvasElement, '.totp-verify .totp-code-input'), code);
}

async function openChangePassword(canvasElement: HTMLElement) {
  await userEvent.click(await element(canvasElement, '[aria-labelledby="account-password-heading"] .btn'));
  const dialog = await visibleElement(canvasElement, '.change-password-card');
  await userEvent.type(await element(dialog, 'input[name="password"]'), 'Croissant2026');
  await userEvent.type(await element(dialog, 'input[name="confirm-password"]'), 'Croissant2026');
}

const meta: Meta = {
  title: 'Screens/Account',
  component: Account,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 900 },
      description: {
        component:
          'The personal account page: profile, password, amount display and the two ways to strengthen sign-in (passkeys and an authenticator app). ' +
          'Everything here belongs to the person, not the company, so it stays reachable for an account without a company, which gets a single call to action at the top. ' +
          'Two columns on desktop, one on tablet and phone. Each block is a flat sheet with one heading; destructive actions (removing a passkey, turning two-factor off) always ask for confirmation first.',
      },
    },
  },
};

export default meta;

export const PasskeysAdded: Story = {
  name: 'Passkeys added, two-factor off',
  render: accountPage(),
  parameters: {
    docs: { description: { story: 'Three passkeys, one never used and one with a long name, and two-factor authentication not yet turned on.' } },
  },
};

export const NoPasskeysTwoFactorOn: Story = {
  name: 'No passkeys, two-factor on',
  render: accountPage({ passkeys: [], totpEnabled: true }),
  parameters: {
    docs: { description: { story: 'The empty passkey list invites the first passkey with one line; two-factor shows its on state.' } },
  },
};

export const TwoFactorSetup: Story = {
  name: 'Setting up two-factor',
  render: accountPage({ passkeys: [] }),
  play: async ({ canvasElement }) => {
    await startTwoFactorSetup(canvasElement, '482913');
  },
  parameters: {
    docs: { description: { story: 'After “Turn on”, the QR code, the manual key behind a disclosure and the 6-digit code field.' } },
  },
};

export const TwoFactorWrongCode: Story = {
  name: 'Two-factor setup with a wrong code',
  render: accountPage({ passkeys: [] }),
  play: async ({ canvasElement }) => {
    await startTwoFactorSetup(canvasElement, '123456');
    await userEvent.click(await element(canvasElement, '.totp-verify .totp-actions .btn.primary'));
    await element(canvasElement, '.totp-verify .form-error');
  },
};

export const RecoveryCodes: Story = {
  name: 'Two-factor recovery codes',
  render: accountPage({ passkeys: [] }),
  play: async ({ canvasElement }) => {
    await startTwoFactorSetup(canvasElement, '482913');
    await userEvent.click(await element(canvasElement, '.totp-verify .totp-actions .btn.primary'));
    await element(canvasElement, '.totp-recovery-grid');
  },
  parameters: {
    docs: { description: { story: 'Shown once, right after two-factor is turned on: the codes, copy and download, and an explicit “I saved my codes”.' } },
  },
};

export const TurningTwoFactorOff: Story = {
  name: 'Turning two-factor off',
  render: accountPage({ totpEnabled: true }),
  play: async ({ canvasElement }) => {
    await userEvent.click(await element(canvasElement, '.totp-status-enabled + div .btn'));
    await element(canvasElement, '.totp-verify .btn.danger');
  },
};

export const ChangePassword: Story = {
  name: 'Change password',
  render: accountPage(),
  play: async ({ canvasElement }) => {
    await openChangePassword(canvasElement);
  },
  parameters: {
    docs: { description: { story: 'The modal shows the strength meter and the rules as they are met; the primary button stays disabled until all of them are.' } },
  },
};

export const ChangePasswordPhone: Story = {
  name: 'Change password on a phone',
  render: accountPage({ passkeys: passkeys.slice(0, 2) }),
  play: async ({ canvasElement }) => {
    await openChangePassword(canvasElement);
  },
  globals: { viewport: { value: 'phone', isRotated: false } },
};

export const WithoutCompany: Story = {
  name: 'Account without a company',
  render: accountPage({ passkeys: passkeys.slice(1, 2), totpEnabled: true }, { company: null }),
  parameters: {
    docs: { description: { story: 'Signed in, but not linked to any company: the band has no company sections and the page leads with “Add company”. The amount display block is absent because there is no VAT number.' } },
  },
};

export const Loading: Story = {
  name: 'Loading',
  render: accountPage({ account: 'loading' }),
};

export const Tablet: Story = {
  name: 'Tablet portrait',
  render: accountPage(),
  globals: { viewport: { value: 'tabletPortrait', isRotated: false } },
};

export const Phone: Story = {
  name: 'Phone',
  render: accountPage(),
  globals: { viewport: { value: 'phone', isRotated: false } },
};

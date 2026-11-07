import { userEvent } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { Login } from '../../src/login/login';
import { ForgotPassword } from '../../src/login/forgot-password';
import { ResetPassword } from '../../src/login/reset-password';
import {
  anonymousMocks,
  browserAuthentication,
  element,
  pageTemplate,
  passwordService,
  routedPage,
  type AnonymousOptions,
  type BrowserAuthenticationOptions,
  type PasswordServiceOptions,
} from '../support/mocks/accounts';

function loginPage(options: BrowserAuthenticationOptions = {}, anonymous: AnonymousOptions = {}) {
  return () => ({
    template: pageTemplate('login'),
    items: [browserAuthentication(options), ...anonymousMocks(anonymous)],
  });
}

function forgotPasswordPage(options: PasswordServiceOptions = {}) {
  return () => ({
    template: pageTemplate('forgot-password'),
    components: [ForgotPassword],
    items: [passwordService(options), ...anonymousMocks({ path: '/forgot-password' })],
  });
}

function resetPasswordPage(options: PasswordServiceOptions = {}) {
  return () => ({
    ...routedPage('reset-password', 'token=storybook-reset-token'),
    components: [ResetPassword],
    items: [passwordService(options), ...anonymousMocks({ path: '/reset-password' })],
  });
}

async function typePasswords(canvasElement: HTMLElement, password: string, confirmation: string) {
  await userEvent.type(await element(canvasElement, 'input[name="password"]'), password);
  await userEvent.type(await element(canvasElement, 'input[name="confirm-password"]'), confirmation);
}

const meta: Meta = {
  title: 'Screens/Sign in',
  component: Login,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 820 },
      description: {
        component:
          'Sign-in screens use the wizard frame: the navy panel with the mark and one sentence on the left, a single sheet on the right, and on phones a slim navy strip above a full-width sheet. ' +
          'The form is left-aligned and at most 440px wide. Errors appear above the primary button, say what happened and what to do next, and never blame the person. ' +
          'Passkeys are offered as a secondary way in under an “or” divider when the browser supports them; two-factor and recovery codes replace the form in the same sheet.',
      },
    },
  },
};

export default meta;

export const SignIn: Story = {
  name: 'Sign in',
  render: loginPage(),
  parameters: {
    docs: { description: { story: 'Empty form with the passkey option, “Forgot your password?” beside the password label and the link to register.' } },
  },
};

export const CheckingSession: Story = {
  name: 'Checking the session',
  render: loginPage({ session: 'loading' }),
};

export const WrongPassword: Story = {
  name: 'Wrong email or password',
  render: loginPage(),
  play: async ({ canvasElement }) => {
    const email = await element<HTMLInputElement>(canvasElement, 'input[name="username"]');
    await userEvent.clear(email);
    await userEvent.type(email, 'lotte@bakkerijdesmet.be');
    await userEvent.type(await element(canvasElement, 'input[name="password"]'), 'not-my-password');
    await userEvent.click(await element(canvasElement, '.login-submit'));
    await element(canvasElement, '.form-error');
  },
};

export const NoCompanyAccess: Story = {
  name: 'Signed in without company access',
  render: loginPage({ session: 'authenticated' }, { authorizationFailure: 'ownership_unavailable' }),
  parameters: {
    docs: { description: { story: 'The credentials were right but the account is not linked to any company, so the sign-in is sent back with an explanation.' } },
  },
};

export const ServiceUnavailable: Story = {
  name: 'Sign-in service unavailable',
  render: loginPage({ session: 'unavailable' }),
};

export const TwoFactor: Story = {
  name: 'Two-factor code',
  render: loginPage({ session: 'totp_required' }),
};

export const TwoFactorWrongCode: Story = {
  name: 'Two-factor code rejected',
  render: loginPage({ session: 'totp_required' }),
  play: async ({ canvasElement }) => {
    await userEvent.type(await element(canvasElement, 'input[name="verificationCode"]'), '130579');
    await element(canvasElement, '.form-error');
  },
};

export const RecoveryCode: Story = {
  name: 'Recovery code',
  render: loginPage({ session: 'totp_required' }),
  play: async ({ canvasElement }) => {
    await userEvent.click(await element(canvasElement, '.auth-link'));
    await element(canvasElement, 'input[name="recoveryCode"]');
  },
};

export const Tablet: Story = {
  name: 'Tablet portrait',
  render: loginPage(),
  globals: { viewport: { value: 'tabletPortrait', isRotated: false } },
};

export const Phone: Story = {
  name: 'Phone',
  render: loginPage(),
  globals: { viewport: { value: 'phone', isRotated: false } },
};

export const PhoneLandscape: Story = {
  name: 'Phone landscape',
  render: loginPage(),
  globals: { viewport: { value: 'phoneLandscape', isRotated: false } },
};

export const ForgotPasswordForm: Story = {
  name: 'Forgot password',
  render: forgotPasswordPage(),
};

export const ForgotPasswordSent: Story = {
  name: 'Reset link sent',
  render: forgotPasswordPage(),
  play: async ({ canvasElement }) => {
    await userEvent.type(await element(canvasElement, 'input[name="email"]'), 'lotte@bakkerijdesmet.be');
    await userEvent.click(await element(canvasElement, 'form .btn.primary'));
    await element(canvasElement, '[role="status"]');
  },
  parameters: {
    docs: { description: { story: 'The same answer whether or not the address has an account, so the page never reveals who is registered.' } },
  },
};

export const ResetPasswordForm: Story = {
  name: 'Choose a new password',
  render: resetPasswordPage(),
};

export const ResetPasswordMismatch: Story = {
  name: 'New passwords do not match',
  render: resetPasswordPage(),
  play: async ({ canvasElement }) => {
    await typePasswords(canvasElement, 'Speculaas&Koffie26', 'Speculaas&Koffie');
  },
  parameters: {
    docs: { description: { story: 'Every rule but the last is met; the save button stays disabled until the two fields match.' } },
  },
};

export const ResetPasswordExpired: Story = {
  name: 'Reset link expired',
  render: resetPasswordPage({ resetPasswordFails: true }),
  play: async ({ canvasElement }) => {
    await typePasswords(canvasElement, 'Speculaas&Koffie26', 'Speculaas&Koffie26');
    await userEvent.click(await element(canvasElement, 'form .btn.primary'));
    await element(canvasElement, '.form-error');
  },
  parameters: {
    docs: { description: { story: 'The token is checked only when the new password is saved; an expired or used link offers a new one.' } },
  },
};

export const ResetPasswordPhone: Story = {
  name: 'Choose a new password on a phone',
  render: resetPasswordPage(),
  play: async ({ canvasElement }) => {
    await userEvent.type(await element(canvasElement, 'input[name="password"]'), 'Speculaas');
  },
  globals: { viewport: { value: 'phone', isRotated: false } },
};

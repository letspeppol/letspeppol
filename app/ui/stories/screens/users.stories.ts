import { userEvent } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { Users } from '../../src/users/users';
import { Invitation } from '../../src/users/invitation';
import { sessionMocks } from '../support/mocks/session';
import { BAKERY_PEPPOL_ID } from '../support/mocks/fixtures';
import {
  anonymousMocks,
  companyUsers,
  companyUserService,
  donationMocks,
  element,
  invitationForExistingAccount,
  pageTemplate,
  routedPage,
  visibleElement,
  type CompanyUserServiceOptions,
} from '../support/mocks/accounts';

function usersPage(options: CompanyUserServiceOptions = {}) {
  return () => ({
    template: pageTemplate('users'),
    items: [
      companyUserService(options),
      ...donationMocks(),
      ...sessionMocks({ path: `/${BAKERY_PEPPOL_ID}/settings/users` }),
    ],
  });
}

function invitationPage(options: CompanyUserServiceOptions = {}, query = 'token=storybook-invitation') {
  return () => ({
    ...routedPage('invitation', query),
    components: [Invitation],
    items: [companyUserService(options), ...anonymousMocks({ path: '/invitation' })],
  });
}

async function openInviteModal(canvasElement: HTMLElement) {
  await element(canvasElement, '.users-matrix tbody tr');
  await userEvent.click(await element(canvasElement, '.section-header .actions .btn.primary'));
  const dialog = await visibleElement(canvasElement, '.invite-card');
  await userEvent.type(await element(dialog, 'input[name="invite-name"]'), 'Ellen Maes');
  await userEvent.type(await element(dialog, 'input[name="invite-email"]'), 'ellen.maes@bakkerijdesmet.be');
  return dialog;
}

async function choosePasswords(canvasElement: HTMLElement) {
  await userEvent.type(await element(canvasElement, 'input[name="password"]'), 'Speculaas&Koffie26');
  await userEvent.type(await element(canvasElement, 'input[name="confirm-password"]'), 'Speculaas&Koffie26');
}

const meta: Meta = {
  title: 'Screens/Users',
  component: Users,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 900 },
      description: {
        component:
          'Users is the administrator’s permission matrix: one row per person, one checkbox per permission, saved as soon as a box is ticked. ' +
          'Permissions that follow from another one (sending implies drafting, which implies reading) are ticked and locked, and the administrator row is always complete and read-only. ' +
          'Status is a pill with text: active, invited with its expiry date, invitation expired, suspended. Row actions are icon buttons on desktop and labelled 44px buttons on phone cards. ' +
          'The invitation page is the other side of the same flow: the invited person checks the company, chooses a password if they are new, and accepts.',
      },
    },
  },
};

export default meta;

export const Matrix: Story = {
  name: 'Permission matrix',
  render: usersPage(),
  parameters: {
    docs: { description: { story: 'The administrator, two active colleagues (one an affiliated accountant with a long name), a pending and an expired invitation, and a suspended user.' } },
  },
};

export const OnlyAdministrator: Story = {
  name: 'Only the administrator',
  render: usersPage({ users: companyUsers.filter((user) => user.type === 'ADMIN') }),
  parameters: {
    docs: { description: { story: 'A company nobody has been invited to yet: the matrix holds just the administrator row.' } },
  },
};

export const Loading: Story = {
  name: 'Loading users',
  render: usersPage({ users: 'loading' }),
};

export const LoadFailed: Story = {
  name: 'Users could not be loaded',
  render: usersPage({ users: 'failed' }),
};

export const InviteUser: Story = {
  name: 'Invite user',
  render: usersPage(),
  play: async ({ canvasElement }) => {
    const dialog = await openInviteModal(canvasElement);
    const send = dialog.querySelectorAll<HTMLInputElement>('.invite-permissions input[type="checkbox"]')[2];
    await userEvent.click(send);
  },
  parameters: {
    docs: { description: { story: 'Name, email and the permissions to start with. Ticking “send” also ticks and locks “draft” and “read”.' } },
  },
};

export const InviteRefused: Story = {
  name: 'Invitation refused',
  render: usersPage({ inviteError: 'user_already_member' }),
  play: async ({ canvasElement }) => {
    const dialog = await openInviteModal(canvasElement);
    await userEvent.click(await element(dialog, '.modal-footer .btn.primary'));
    await element(dialog, '.form-error');
  },
};

export const InvitePhone: Story = {
  name: 'Invite user on a phone',
  render: usersPage(),
  play: async ({ canvasElement }) => {
    await openInviteModal(canvasElement);
  },
  globals: { viewport: { value: 'phone', isRotated: false } },
};

export const Tablet: Story = {
  name: 'Tablet portrait',
  render: usersPage(),
  globals: { viewport: { value: 'tabletPortrait', isRotated: false } },
};

export const Phone: Story = {
  name: 'Phone',
  render: usersPage(),
  globals: { viewport: { value: 'phone', isRotated: false } },
};

export const InvitationNewAccount: Story = {
  name: 'Invitation for a new account',
  render: invitationPage(),
  play: async ({ canvasElement }) => {
    await choosePasswords(canvasElement);
  },
  parameters: {
    docs: { description: { story: 'The person has no account yet, so they choose a password before accepting.' } },
  },
};

export const InvitationExistingAccount: Story = {
  name: 'Invitation for an existing account',
  render: invitationPage({ invitation: invitationForExistingAccount }),
};

export const InvitationAccepted: Story = {
  name: 'Invitation accepted',
  render: invitationPage({ invitation: invitationForExistingAccount }),
  play: async ({ canvasElement }) => {
    await userEvent.click(await element(canvasElement, 'form.auth-form .btn.primary'));
    await element(canvasElement, '.auth-form [role="status"]');
  },
};

export const InvitationExpired: Story = {
  name: 'Invitation expired',
  render: invitationPage({ invitation: { errorCode: 'invitation_expired' } }),
};

export const InvitationInvalid: Story = {
  name: 'Invitation link invalid',
  render: invitationPage({}, ''),
  parameters: {
    docs: { description: { story: 'Opened without a token, or with one that was already used.' } },
  },
};

export const InvitationPhone: Story = {
  name: 'Invitation on a phone',
  render: invitationPage(),
  globals: { viewport: { value: 'phone', isRotated: false } },
};

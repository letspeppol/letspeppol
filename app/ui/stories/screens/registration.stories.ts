import { userEvent } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { Registration } from '../../src/registration/registration';
import { Onboarding } from '../../src/registration/onboarding';
import { EmailConfirmation } from '../../src/registration/email-confirmation';
import { AddOwnership } from '../../src/registration/add-ownership';
import { KYCApi } from '../../src/services/kyc/kyc-api';
import { sessionMocks } from '../support/mocks/session';
import { BAKERY_PEPPOL_ID } from '../support/mocks/fixtures';
import { provide, stub } from '../support/mocks/stub';
import {
  accountServices,
  anonymousMocks,
  element,
  kycCompanyWithAdmin,
  pageTemplate,
  peppolDirService,
  registrationService,
  routedPage,
  type RegistrationServiceOptions,
} from '../support/mocks/accounts';

function registrationPage(options: RegistrationServiceOptions = {}, data: Record<string, unknown> = { registrationType: 'ADMIN' }) {
  return () => ({
    ...routedPage('registration', '', data),
    items: [registrationService(options), ...anonymousMocks({ path: '/registration' })],
  });
}

function onboardingPage(query = '') {
  return () => ({
    ...routedPage('onboarding', query),
    components: [Onboarding],
    items: anonymousMocks({ path: '/onboarding' }),
  });
}

function emailConfirmationPage(options: RegistrationServiceOptions = {}, registeredElsewhere = false, query = 'token=storybook-email-token') {
  return () => ({
    ...routedPage('email-confirmation', query),
    components: [EmailConfirmation],
    items: [
      registrationService(options),
      peppolDirService(registeredElsewhere),
      provide(KYCApi, stub<KYCApi>('KYCApi', {})),
      ...anonymousMocks({ path: '/email-confirmation' }),
    ],
  });
}

function addOwnershipPage(options: RegistrationServiceOptions = {}) {
  return () => ({
    template: pageTemplate('add-ownership'),
    components: [AddOwnership],
    items: [
      registrationService(options),
      ...accountServices(),
      ...sessionMocks({ path: `/${BAKERY_PEPPOL_ID}/add-ownership` }),
    ],
  });
}

async function findCompany(canvasElement: HTMLElement, withEmail = true) {
  await userEvent.type(await element(canvasElement, 'input[name="enterpriseNumber"]'), '0798.765.432');
  if (withEmail) {
    await userEvent.type(await element(canvasElement, 'input[name="email"]'), 'tom@loodgieterijjanssens.be');
  }
  await userEvent.click(await element(canvasElement, 'form .btn.primary'));
}

async function confirmCompany(canvasElement: HTMLElement) {
  await findCompany(canvasElement);
  await element(canvasElement, 'dl.wizard-facts');
  await userEvent.click(await element(canvasElement, 'form .btn.primary'));
}

const meta: Meta = {
  title: 'Screens/Registration',
  component: Registration,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 860 },
      description: {
        component:
          'Registration is the one real sequence in the product, so it is the one place with a numbered stepper: company, details, email, identity, contract. ' +
          'The first three steps happen here; the email link continues at identity and contract, and the stepper keeps counting across both pages. ' +
          'Each step asks for one thing, shows the facts it found as a definition list, and keeps the way back (“Search again”, “Sign in”) as a quiet link under the primary action. ' +
          'The onboarding checklist and the eID steps use the wide wizard sheet with two columns on desktop that stack on tablet and phone. “Add company” reuses the same pattern for a signed-in person adding a second company.',
      },
    },
  },
};

export default meta;

export const FindCompany: Story = {
  name: 'Step 1: find your company',
  render: registrationPage(),
};

export const CompanyNotFound: Story = {
  name: 'Company not found',
  render: registrationPage({ company: 'not-found' }),
  play: async ({ canvasElement }) => {
    await findCompany(canvasElement);
    await element(canvasElement, '.form-error');
  },
};

export const ConfirmCompany: Story = {
  name: 'Step 2: confirm the company',
  render: registrationPage(),
  play: async ({ canvasElement }) => {
    await findCompany(canvasElement);
    await element(canvasElement, 'dl.wizard-facts');
  },
};

export const AlreadyRegistered: Story = {
  name: 'Company already registered',
  render: registrationPage({ confirmFails: true }),
  play: async ({ canvasElement }) => {
    await confirmCompany(canvasElement);
    await element(canvasElement, '.form-error');
  },
};

export const CheckEmail: Story = {
  name: 'Step 3: check your email',
  render: registrationPage(),
  play: async ({ canvasElement }) => {
    await confirmCompany(canvasElement);
    await element(canvasElement, 'div.auth-form .btn.primary');
  },
};

export const Affiliate: Story = {
  name: 'Affiliate registration',
  render: registrationPage({}, { registrationType: 'AFFILIATE' }),
  parameters: {
    docs: { description: { story: 'Reached from /affiliate/registration: an accountant registering for a client company, with its own title and intro.' } },
  },
};

export const ConfirmCompanyPhone: Story = {
  name: 'Step 2 on a phone',
  render: registrationPage(),
  play: async ({ canvasElement }) => {
    await findCompany(canvasElement);
    await element(canvasElement, 'dl.wizard-facts');
  },
  globals: { viewport: { value: 'phone', isRotated: false } },
};

export const OnboardingChecklist: Story = {
  name: 'Onboarding checklist',
  render: onboardingPage(),
  parameters: {
    docs: { description: { story: 'What to install before the eID steps, with the Web eID check and troubleshooting tips beside it.' } },
  },
};

export const OnboardingTablet: Story = {
  name: 'Onboarding checklist on a tablet',
  render: onboardingPage('flow=affiliate'),
  globals: { viewport: { value: 'tabletPortrait', isRotated: false } },
};

export const OnboardingPhone: Story = {
  name: 'Onboarding checklist on a phone',
  render: onboardingPage(),
  globals: { viewport: { value: 'phone', isRotated: false } },
};

export const EmailConfirmationPending: Story = {
  name: 'Email confirmation: checking the link',
  render: emailConfirmationPage({ token: 'loading' }),
};

export const EmailConfirmed: Story = {
  name: 'Email confirmation: choose your director',
  render: emailConfirmationPage(),
  parameters: {
    docs: { description: { story: 'The link is valid: the company is shown and the person picks which director they are before confirming with their eID.' } },
  },
};

export const EmailConfirmedRegisteredElsewhere: Story = {
  name: 'Email confirmation: already on Peppol elsewhere',
  render: emailConfirmationPage({}, true),
};

export const EmailConfirmationInvalid: Story = {
  name: 'Email confirmation: link invalid',
  render: emailConfirmationPage({ token: 'invalid' }),
};

export const EmailConfirmedPhone: Story = {
  name: 'Email confirmation on a phone',
  render: emailConfirmationPage(),
  globals: { viewport: { value: 'phone', isRotated: false } },
};

export const AddCompany: Story = {
  name: 'Add company: find company',
  render: addOwnershipPage(),
};

export const AddCompanyNotFound: Story = {
  name: 'Add company: not found',
  render: addOwnershipPage({ company: 'not-found' }),
  play: async ({ canvasElement }) => {
    await findCompany(canvasElement, false);
    await element(canvasElement, '.form-error');
  },
};

export const AddCompanyHasAdministrator: Story = {
  name: 'Add company: already has an administrator',
  render: addOwnershipPage({ company: kycCompanyWithAdmin }),
  play: async ({ canvasElement }) => {
    await findCompany(canvasElement, false);
    await element(canvasElement, '.wizard-notice');
  },
};

export const AddCompanyDirectors: Story = {
  name: 'Add company: choose your director',
  render: addOwnershipPage(),
  play: async ({ canvasElement }) => {
    await findCompany(canvasElement, false);
    await element(canvasElement, '.director-list');
  },
};

export const AddCompanyPhone: Story = {
  name: 'Add company on a phone',
  render: addOwnershipPage(),
  play: async ({ canvasElement }) => {
    await findCompany(canvasElement, false);
    await element(canvasElement, '.director-list');
  },
  globals: { viewport: { value: 'phone', isRotated: false } },
};

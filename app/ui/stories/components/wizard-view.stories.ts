import type { Meta, Story } from '../support/story';
import { WizardView } from '../../src/components/view/wizard-view';
import { Stepper } from '../../src/components/stepper/stepper';
import { CardReader } from '../../src/components/eid/card-reader';
import { routerMocks } from '../support/mocks/session';
import { bakery } from '../support/mocks/fixtures';

const steps = [
  'registration.step.company',
  'registration.step.details',
  'registration.step.email',
  'registration.step.identity',
  'registration.step.contract',
];

const signIn = `
  <wizard-view>
    <h1 au-slot="header" t="login.title">Sign in</h1>
    <form class="auth-form ds-stack" submit.trigger="$event.preventDefault()">
      <label class="field">
        <span t="label.email">Email</span>
        <input type="email" value="lotte@bakkerijdesmet.be" autocomplete="off" />
      </label>
      <label class="field">
        <span t="label.password">Password</span>
        <input type="password" autocomplete="off" />
      </label>
      <div class="auth-actions">
        <button type="submit" class="btn primary" t="login.continue">Sign in</button>
      </div>
    </form>
  </wizard-view>
`;

const registration = `
  <wizard-view>
    <stepper au-slot="stepper" steps.bind="steps" completed-steps.bind="1"></stepper>
    <h1 au-slot="header" t="registration.title-step1">Confirm your company</h1>
    <div class="auth-form ds-stack">
      <p class="wizard-lead" t="registration.confirm-intro">Check that these are your company’s details.</p>
      <dl class="wizard-facts">
        <div><dt t="label.name">Name</dt><dd>\${company.name}</dd></div>
        <div><dt t="label.enterprise-number">Enterprise number</dt><dd class="tabular">\${company.identifier}</dd></div>
        <div><dt t="label.vat-number">VAT number</dt><dd class="tabular">\${company.vatNumber}</dd></div>
        <div><dt t="label.address">Address</dt><dd>\${company.registeredOffice.street} \${company.registeredOffice.houseNumber}<br>\${company.registeredOffice.postalCode} \${company.registeredOffice.city}</dd></div>
      </dl>
      <div class="auth-actions">
        <button type="button" class="btn primary" t="registration.confirm-company">Confirm company</button>
      </div>
    </div>
  </wizard-view>
`;

const wide = `
  <wizard-view wide.bind="true">
    <stepper au-slot="stepper" steps.bind="steps" completed-steps.bind="3"></stepper>
    <h1 au-slot="header" t="confirmation.header">Confirm your identity</h1>
    <div class="wizard-columns">
      <div class="wizard-section">
        <h2 t="confirmation.company">Company</h2>
        <dl class="wizard-facts">
          <div><dt t="label.name">Name</dt><dd>\${company.name}</dd></div>
          <div><dt t="label.enterprise-number">Enterprise number</dt><dd class="tabular">\${company.identifier}</dd></div>
        </dl>
      </div>
      <div class="wizard-section">
        <h2 t="confirmation.validate-eid-title">Confirm with your eID</h2>
        <card-reader caption-key="confirmation.anim-caption-validate" reader-label-key="confirmation.anim-reader-label"></card-reader>
      </div>
    </div>
  </wizard-view>
`;

const frame = (template: string) => () => ({
  template,
  components: [WizardView, Stepper, CardReader],
  items: routerMocks('/login'),
  props: { steps, company: bakery },
});

const meta: Meta = {
  title: 'Components/Wizard view',
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 720 },
      description: {
        component:
          'The frame for every screen before or outside the app: sign-in, registration, onboarding, invitations and password reset. ' +
          'A navy panel carries the globe mark and one sentence of promise; the form sits on a single sheet, left-aligned and at most 440 px wide, or 880 px in the wide variant used for side-by-side steps such as eID confirmation and the contract. ' +
          'Slots: `stepper` above everything, `header` for the h1, and the default slot for the form. Theme and language sit top right. ' +
          'Under 1024 px the panel collapses into a navy strip above the sheet; nothing has a fixed height, so the sheet scrolls.',
      },
    },
  },
};

export default meta;

export const SignIn: Story = {
  name: 'Sign in',
  render: frame(signIn),
};

export const Registration: Story = {
  name: 'Registration step with stepper',
  render: frame(registration),
};

export const Wide: Story = {
  name: 'Wide, two columns',
  render: frame(wide),
};

export const Tablet: Story = {
  name: 'Tablet portrait',
  globals: { viewport: { value: 'tabletPortrait', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 900 } } },
  render: frame(registration),
};

export const Phone: Story = {
  name: 'Phone',
  globals: { viewport: { value: 'phone', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 844 } } },
  render: frame(signIn),
};

export const PhoneWide: Story = {
  name: 'Phone, wide variant',
  globals: { viewport: { value: 'phone', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 844 } } },
  render: frame(wide),
};

export const PhoneLandscape: Story = {
  name: 'Phone landscape',
  globals: { viewport: { value: 'phoneLandscape', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 390 } } },
  render: frame(signIn),
};

import type { Meta, Story } from '../support/story';
import { ApplicationView } from '../../src/components/view/application-view';
import { sessionMocks, type SessionOptions } from '../support/mocks/session';
import { donationMocks } from '../support/mocks/components';
import { atelier, ownerships, partners } from '../support/mocks/fixtures';

const shell = (session: SessionOptions = {}) => () => ({
  template: `
    <application-view>
      <div class="section-header">
        <h1 t="navigation.partners">Partners</h1>
        <button type="button" class="btn primary" t="partner.edit.new">New partner</button>
      </div>
      <section class="card">
        <ul style="margin: 0; padding-left: var(--space-5)">
          <li repeat.for="partner of partners">\${partner.name}</li>
        </ul>
      </section>
    </application-view>
  `,
  components: [ApplicationView],
  items: [...sessionMocks(session), ...donationMocks()],
  props: { partners },
});

const notOnPeppol = ownerships.map((ownership) => ({ ...ownership, peppolActive: false }));

const meta: Meta = {
  title: 'Components/Application view',
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 640 },
      description: {
        component:
          'The frame every signed-in screen is projected into: the navy app band on top, the page in the main container on the ground, and the donation dock at the end. ' +
          'When the current company is not registered on Peppol through Let’s Peppol, a banner sits between band and page; admins get a “Solve this now” action that opens registration in Settings, other users only see the message. ' +
          'Page content here is a placeholder: a section header with one primary action and one sheet, as DESIGN.md prescribes.',
      },
    },
  },
};

export default meta;

export const Desktop: Story = {
  render: shell(),
};

export const NotRegisteredAdmin: Story = {
  name: 'Not on Peppol, admin',
  render: shell({ ownerships: notOnPeppol }),
};

export const NotRegisteredUser: Story = {
  name: 'Not on Peppol, user',
  render: shell({ company: atelier, admin: false, ownerships: notOnPeppol }),
};

export const Tablet: Story = {
  name: 'Tablet portrait',
  globals: { viewport: { value: 'tabletPortrait', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 900 } } },
  render: shell(),
};

export const Phone: Story = {
  name: 'Phone, not on Peppol',
  globals: { viewport: { value: 'phone', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 844 } } },
  render: shell({ ownerships: notOnPeppol }),
};

export const PhoneLandscape: Story = {
  name: 'Phone landscape',
  globals: { viewport: { value: 'phoneLandscape', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 390 } } },
  render: shell(),
};

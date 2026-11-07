import { userEvent } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { Heading } from '../../src/components/heading/heading';
import { sessionMocks } from '../support/mocks/session';
import { featureAckContainer } from '../support/mocks/components';

const features = { 'story-invite-user': 'settings', 'story-passkeys': 'account' };

const page = (acknowledged: string[] = []) => () => ({
  container: featureAckContainer(features, acknowledged),
  template: `
    <heading></heading>
    <main class="container ds-stack" style="max-width: 48rem">
      <div class="ds-row">
        <button type="button" class="btn primary" new-feature="story-invite-user"><span t="users.invite.action">Invite user</span></button>
        <button type="button" class="btn" t="common.edit">Edit</button>
      </div>
      <section class="card" new-feature="story-passkeys">
        <h2 t="passkey.title">Passkeys</h2>
        <p t="passkey.empty"></p>
        <div><button type="button" class="btn" t="passkey.add">Add passkey</button></div>
      </section>
    </main>
  `,
  components: [Heading],
  items: sessionMocks(),
});

const meta: Meta = {
  title: 'Components/New feature marker',
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 420 },
      description: {
        component:
          'Points people at something that changed. `new-feature="id"` goes on the new thing itself and draws a moving rainbow border with a “New” pill; by default the first click dismisses it, and `trigger` can make that a hover, a few seconds in view, or never. ' +
          '`new-feature-section="section"` goes on navigation and lights up the link, with a corner pill, while any feature registered for that section has not been seen, so people can find their way to it. ' +
          'Seen features are remembered per device, and every entry in `feature-registry.ts` has an expiry date after which the marker disappears for everyone. ' +
          'Here the Invite user button belongs to Settings and the Passkeys card to Account.',
      },
    },
  },
};

export default meta;

export const NotSeen: Story = {
  name: 'Not seen yet',
  render: page(),
};

export const Seen: Story = {
  name: 'Seen',
  render: page([...Object.keys(features), 'add-account']),
};

export const Dismissing: Story = {
  name: 'Card clicked',
  parameters: {
    docs: { description: { story: 'Clicking the Passkeys card dismisses its marker and the Account link stops glowing; Invite user and Settings stay marked.' } },
  },
  render: page(),
  play: async ({ canvasElement }) => {
    const card = canvasElement.querySelector<HTMLElement>('section.card');
    if (card) {
      await userEvent.click(card);
    }
  },
};

export const Phone: Story = {
  name: 'Phone, markers inside More',
  globals: { viewport: { value: 'phone', isRotated: false } },
  parameters: {
    docs: {
      story: { inline: false, iframeHeight: 760 },
      description: { story: 'Settings and Account live in the More sheet on phones, so their markers only show once it is open; the More button itself carries no marker.' },
    },
  },
  render: page(),
  play: async ({ canvasElement }) => {
    const more = canvasElement.querySelector<HTMLElement>('.bottom-bar button.bar-item');
    if (more) {
      await userEvent.click(more);
    }
  },
};

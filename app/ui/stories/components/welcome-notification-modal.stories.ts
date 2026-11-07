import { userEvent, within } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { WelcomeNotificationModal } from '../../src/components/welcome-notification/welcome-notification-modal';
import { sessionMocks } from '../support/mocks/session';
import { welcomeNotificationMocks, welcomeNotifications } from '../support/mocks/components';
import type { WelcomeNotificationsResponse } from '../../src/services/app/welcome-notification-service';

const modal = (response: WelcomeNotificationsResponse = welcomeNotifications) => () => ({
  template: '<welcome-notification-modal></welcome-notification-modal>',
  components: [WelcomeNotificationModal],
  items: [...sessionMocks(), ...welcomeNotificationMocks(response)],
});

const pressNext = (times: number) => async ({ canvasElement }: { canvasElement: HTMLElement }) => {
  await within(canvasElement).findByText(/1 \/ 3/);
  for (let i = 0; i < times; i++) {
    const next = canvasElement.querySelector<HTMLButtonElement>('.welcome-notification-card .modal-footer .btn.primary');
    if (next) {
      await userEvent.click(next);
    }
  }
};

const meta: Meta = {
  title: 'Components/Welcome notification modal',
  parameters: {
    docs: {
      story: { inline: false, iframeHeight: 560 },
      description: {
        component:
          'Shows announcements written by the Let’s Peppol team right after sign-in: a welcome for new companies, new features, planned maintenance. ' +
          'The content is HTML from the server in the four languages, falling back to English when a translation is missing, so it only gets body typography and no layout of its own. ' +
          'Several notices are paged with Next and a counter; Close or Escape on the last one marks them as seen on this device. ' +
          'Keep each notice to a heading and a short paragraph or list: this interrupts the person on the way to their work.',
      },
    },
  },
};

export default meta;

export const FirstOfThree: Story = {
  name: 'First of three',
  render: modal(),
};

export const Fallback: Story = {
  name: 'Second, English fallback',
  parameters: {
    docs: { description: { story: 'This notice has Dutch and English only; switch the toolbar to French or German to see the English fallback.' } },
  },
  render: modal(),
  play: pressNext(1),
};

export const Last: Story = {
  name: 'Last of three',
  render: modal(),
  play: pressNext(2),
};

export const Single: Story = {
  name: 'Single notice',
  render: modal({ ...welcomeNotifications, notifications: welcomeNotifications.notifications.slice(0, 1) }),
};

export const Phone: Story = {
  name: 'Phone bottom sheet',
  globals: { viewport: { value: 'phone', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 700 } } },
  render: modal(),
};

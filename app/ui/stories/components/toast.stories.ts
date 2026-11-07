import { bindable, customElement, IEventAggregator } from 'aurelia';
import { resolve } from '@aurelia/kernel';
import { I18N } from '@aurelia/i18n';
import type { Meta, Story } from '../support/story';
import { Alert, AlertType } from '../../src/components/alert/alert';

interface ScriptedToast {
  type: AlertType;
  key: string;
  params?: Record<string, unknown>;
}

@customElement({
  name: 'toast-publisher',
  template: '<button type="button" class="btn" click.trigger="publish()">Publish again</button>',
})
class ToastPublisher {
  @bindable messages: ScriptedToast[] = [];
  @bindable region?: Alert;
  @bindable hold = true;
  private readonly ea = resolve(IEventAggregator);
  private readonly i18n = resolve(I18N);

  attached() {
    this.publish();
  }

  publish() {
    for (const message of this.messages) {
      this.ea.publish('alert', { alertType: message.type, text: this.i18n.tr(message.key, message.params) });
    }
    if (this.hold && this.region) {
      for (const toast of this.region.toasts) {
        this.region.pause(toast);
      }
    }
  }
}

const render = () => ({
  template: `
    <alert component.ref="region"></alert>
    <toast-publisher messages.bind="messages" region.bind="region" hold.bind="hold"></toast-publisher>
  `,
  components: [Alert, ToastPublisher],
});

const meta: Meta = {
  title: 'Components/Toast',
  render,
  args: { hold: true },
  argTypes: {
    hold: { control: 'boolean', description: 'Keep toasts on screen, as if hovered. Off shows the real 5 s / 10 s auto-dismiss.' },
    messages: { control: 'object' },
  },
  parameters: {
    docs: {
      story: { inline: false, iframeHeight: 360 },
      description: {
        component:
          'Toasts confirm what just happened or report a failure, using the same verb as the button that caused it (“Send invoice” is followed by “Invoice sent”). ' +
          'Any screen publishes `alert` on the event aggregator; the single `<alert>` region in the app shell shows at most three at once, top right under the band on desktop and above the bottom bar on phones. ' +
          'Success, info and neutral leave after 5 seconds, warning and danger after 10 seconds and are announced as alerts; hovering or focusing a toast pauses it. ' +
          'The coloured edge and icon are never the only signal: every toast carries a title and a sentence.',
      },
    },
  },
};

export default meta;

export const Success: Story = {
  args: { messages: [{ type: AlertType.Success, key: 'alert.invoice.sent.INVOICE' }] },
};

export const Info: Story = {
  args: { messages: [{ type: AlertType.Info, key: 'alert.attachment.downloaded', params: { filename: 'INV-2026-0141.pdf' } }] },
};

export const Warning: Story = {
  args: { messages: [{ type: AlertType.Warning, key: 'alert.download.limit-reached' }] },
};

export const Danger: Story = {
  name: 'Danger (error)',
  args: { messages: [{ type: AlertType.Danger, key: 'alert.invoice.send-failed.INVOICE' }] },
};

export const Neutral: Story = {
  parameters: {
    docs: { description: { story: 'Defined for completeness; no screen publishes a neutral toast today.' } },
  },
  args: { messages: [{ type: AlertType.Neutral, key: 'alert.invoice.marked-seen' }] },
};

export const Stacked: Story = {
  name: 'Stacked, oldest drops out',
  parameters: {
    docs: { description: { story: 'Four toasts were published; only the newest three stay, newest at the bottom.' } },
  },
  args: {
    messages: [
      { type: AlertType.Info, key: 'alert.download.created' },
      { type: AlertType.Success, key: 'alert.partner.created' },
      { type: AlertType.Warning, key: 'alert.peppol-search.no-results' },
      { type: AlertType.Danger, key: 'alert.partner.update-failed' },
    ],
  },
};

export const LongMessage: Story = {
  name: 'Long message',
  args: {
    messages: [
      { type: AlertType.Warning, key: 'alert.users.invited-not-sent' },
      { type: AlertType.Danger, key: 'alert.account.receiving-conflict' },
    ],
  },
};

export const Phone: Story = {
  name: 'Phone, above the bottom bar',
  globals: { viewport: { value: 'phone', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 640 } } },
  args: {
    messages: [
      { type: AlertType.Success, key: 'alert.invoice.draft-saved.INVOICE' },
      { type: AlertType.Danger, key: 'alert.invoice.send-failed.INVOICE' },
    ],
  },
};

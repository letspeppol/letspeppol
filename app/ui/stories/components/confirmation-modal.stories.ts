import { bindable, customElement } from 'aurelia';
import { resolve } from '@aurelia/kernel';
import { I18N } from '@aurelia/i18n';
import type { Meta, Story } from '../support/story';
import { ConfirmationModal } from '../../src/components/confirmation/confirmation-modal';
import { ConfirmationModalContext } from '../../src/components/confirmation/confirmation-modal-context';
import { partners } from '../support/mocks/fixtures';

interface ConfirmationRequest {
  titleKey: string;
  textKey: string;
  params?: Record<string, unknown>;
  variant?: 'default' | 'warning';
  confirmKey?: string;
  destructive?: boolean;
}

@customElement({
  name: 'confirmation-request',
  template: '<button type="button" class="btn" click.trigger="ask()">Ask again</button>',
})
class ConfirmationRequester {
  @bindable request?: ConfirmationRequest;
  private readonly context = resolve(ConfirmationModalContext);
  private readonly i18n = resolve(I18N);

  attached() {
    this.ask();
  }

  ask() {
    const request = this.request;
    if (!request) {
      return;
    }
    this.context.showConfirmationModal(
      this.i18n.tr(request.titleKey),
      this.i18n.tr(request.textKey, request.params),
      () => console.info('[storybook] confirmed'),
      () => console.info('[storybook] cancelled'),
      request.variant ?? 'default',
      {
        confirmLabel: request.confirmKey ? this.i18n.tr(request.confirmKey) : undefined,
        destructive: request.destructive,
      },
    );
  }
}

const meta: Meta = {
  title: 'Components/Confirmation modal',
  render: () => ({
    template: `
      <confirmation-request request.bind="request"></confirmation-request>
      <confirmation-modal></confirmation-modal>
    `,
    components: [ConfirmationModal, ConfirmationRequester],
  }),
  parameters: {
    docs: {
      story: { inline: false, iframeHeight: 420 },
      description: {
        component:
          'Asks for a yes or no before an action that is hard to undo or that changes what other people see. ' +
          'Screens call `ConfirmationModalContext.showConfirmationModal()`; the modal itself is placed once per page. ' +
          'The confirm button names the result (“Delete partner”, “Stop receiving”) and turns red only when the action destroys or removes something; the warning variant adds an icon for changes with consequences outside the app, such as Peppol registration. ' +
          'Escape and the backdrop cancel, Enter confirms. On phones it becomes a bottom sheet with full-width buttons.',
      },
    },
  },
};

export default meta;

export const Default: Story = {
  name: 'Default labels',
  parameters: {
    docs: { description: { story: 'Without custom labels the buttons fall back to Cancel and Confirm.' } },
  },
  args: {
    request: { titleKey: 'account.allow-sending', textKey: 'account.confirm.sending-enable' },
  },
};

export const Destructive: Story = {
  name: 'Destructive',
  args: {
    request: {
      titleKey: 'partner.delete',
      textKey: 'partner.delete-confirm',
      params: { name: partners[3].name },
      confirmKey: 'partner.delete',
      destructive: true,
    },
  },
};

export const WarningReversible: Story = {
  name: 'Warning, reversible',
  args: {
    request: {
      titleKey: 'account.confirm.receiving-enable-title',
      textKey: 'account.confirm.receiving-enable',
      variant: 'warning',
      confirmKey: 'account.confirm.receiving-enable-action',
    },
  },
};

export const WarningDestructive: Story = {
  name: 'Warning, destructive',
  args: {
    request: {
      titleKey: 'account.remove-sending',
      textKey: 'account.confirm.sending-disable',
      variant: 'warning',
      confirmKey: 'account.confirm.unregister-action',
      destructive: true,
    },
  },
};

export const Phone: Story = {
  name: 'Phone bottom sheet',
  globals: { viewport: { value: 'phone', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 640 } } },
  args: { ...Destructive.args },
};

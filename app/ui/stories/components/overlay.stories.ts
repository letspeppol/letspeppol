import { bindable, customElement, IEventAggregator } from 'aurelia';
import { resolve } from '@aurelia/kernel';
import { I18N } from '@aurelia/i18n';
import type { Meta, Story } from '../support/story';
import { Overlay } from '../../src/components/overlay/overlay';
import { partners } from '../support/mocks/fixtures';

@customElement({ name: 'overlay-trigger', template: '' })
class OverlayTrigger {
  @bindable textKey = '';
  private readonly ea = resolve(IEventAggregator);
  private readonly i18n = resolve(I18N);

  attached() {
    this.ea.publish('showOverlay', this.textKey ? this.i18n.tr(this.textKey) : '');
  }
}

const meta: Meta = {
  title: 'Components/Overlay',
  render: () => ({
    template: `
      <section class="card" style="max-width: 40rem">
        <h2 t="navigation.partners">Partners</h2>
        <ul>
          <li repeat.for="partner of partners">\${partner.name}</li>
        </ul>
      </section>
      <overlay></overlay>
      <overlay-trigger text-key.bind="textKey"></overlay-trigger>
    `,
    components: [Overlay, OverlayTrigger],
    props: { partners: partners.slice(0, 3) },
  }),
  args: { textKey: 'overlay.sending.INVOICE' },
  argTypes: {
    textKey: {
      control: 'select',
      options: ['', 'overlay.sending.INVOICE', 'overlay.validating', 'overlay.creating-draft', 'overlay.searching-company', 'overlay.saving'],
    },
  },
  parameters: {
    docs: {
      story: { inline: false, iframeHeight: 420 },
      description: {
        component:
          'Blocks the whole screen with a scrim, a spinner and one line while an action that must not be interrupted runs: sending or validating an invoice, creating a draft, saving the account, looking up a company. ' +
          'Screens publish `showOverlay` with the text and `hideOverlay` when done; there is one overlay in the app shell. ' +
          'The line says what is happening in the present tense (“Sending invoice, please wait”) and is announced to screen readers. ' +
          'Use it only for waits of a few seconds; anything longer deserves progress shown in place.',
      },
    },
  },
};

export default meta;

export const Sending: Story = {
  name: 'Sending an invoice',
};

export const Validating: Story = {
  name: 'Validating a document',
  args: { textKey: 'overlay.validating' },
};

export const WithoutText: Story = {
  name: 'Spinner only',
  args: { textKey: '' },
};

export const Phone: Story = {
  name: 'Phone',
  globals: { viewport: { value: 'phone', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 640 } } },
};

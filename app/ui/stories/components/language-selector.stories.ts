import type { Meta, Story } from '../support/story';
import { LanguageSelector } from '../../src/components/lang/language-selector';

const meta: Meta = {
  title: 'Components/Language selector',
  parameters: {
    docs: {
      description: {
        component:
          'A compact native select with the four interface languages as two-letter codes. It switches the language immediately and remembers the choice on this device. ' +
          'It appears in the navy band on desktop, in the More sheet on phones, and in the top corner of the sign-in and registration screens. ' +
          'Inside `.on-navy` it switches to white text, a translucent border and a white chevron; on the ground it uses the sheet colours.',
      },
    },
  },
};

export default meta;

export const OnGround: Story = {
  name: 'On the ground',
  render: () => ({
    template: '<language-selector></language-selector>',
    components: [LanguageSelector],
  }),
};

export const OnBand: Story = {
  name: 'On the navy band',
  render: () => ({
    template: `
      <div class="on-navy ds-row" style="padding: var(--space-3) var(--space-4); background: var(--band); border-radius: var(--radius)">
        <language-selector></language-selector>
      </div>
    `,
    components: [LanguageSelector],
  }),
};

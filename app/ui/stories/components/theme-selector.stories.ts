import type { Meta, Story } from '../support/story';
import { ThemeSelector } from '../../src/components/theme/theme-selector';

const meta: Meta = {
  title: 'Components/Theme selector',
  parameters: {
    docs: {
      description: {
        component:
          'An icon-only toggle between the light and dark theme: a sun in light mode, a moon in dark mode, with an accessible name and pressed state. ' +
          'Without a saved choice the app follows the system preference; pressing it saves an explicit choice on this device. ' +
          'It sits in the navy band and on the auth screens; on phones the same switch lives in the More sheet as a labelled row. ' +
          'Use the Theme toolbar to see both icons; pressing the button here switches the whole preview.',
      },
    },
  },
};

export default meta;

export const OnGround: Story = {
  name: 'On the ground',
  render: () => ({
    template: '<theme-selector></theme-selector>',
    components: [ThemeSelector],
  }),
};

export const OnBand: Story = {
  name: 'On the navy band',
  render: () => ({
    template: `
      <div class="on-navy ds-row" style="padding: var(--space-3) var(--space-4); background: var(--band); border-radius: var(--radius)">
        <theme-selector></theme-selector>
      </div>
    `,
    components: [ThemeSelector],
  }),
};

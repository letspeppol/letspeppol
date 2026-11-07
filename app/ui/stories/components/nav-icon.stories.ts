import type { Meta, Story } from '../support/story';
import { NavIcon, type NavIconName } from '../../src/components/heading/nav-icon';

const sections: NavIconName[] = ['dashboard', 'invoices', 'partners', 'products', 'downloads', 'settings', 'account', 'sponsors'];
const controls: NavIconName[] = ['more', 'menu', 'close', 'switch-company', 'chevron', 'add-company', 'log-out', 'moon', 'sun', 'language', 'user'];

const surfaces = {
  band: 'class="on-navy" style="color: var(--on-navy); background: var(--band)',
  sheet: 'style="color: var(--ink); background: var(--sheet); border: 1px solid var(--rule)',
};

const gallery = (names: 'sections' | 'controls', surface: keyof typeof surfaces) => `
  <div ${surfaces[surface]}; display: grid; grid-template-columns: repeat(auto-fill, minmax(9rem, 1fr)); gap: var(--space-4); padding: var(--space-4); border-radius: var(--radius)">
    <div repeat.for="name of ${names}" class="ds-stack" style="justify-items: start; gap: var(--space-2)">
      <nav-icon name.bind="name"></nav-icon>
      <code style="color: inherit; opacity: .8; font-size: var(--text-sm)">\${name}</code>
    </div>
  </div>
`;

const meta: Meta = {
  title: 'Components/Nav icons',
  render: () => ({
    template: `
      <div class="ds-page">
        <ds-section heading="Sections" lead="One per navigation destination, shown before the label in the band, the bottom bar and the menu.">
          <div class="ds-stack">
            ${gallery('sections', 'band')}
            ${gallery('sections', 'sheet')}
          </div>
        </ds-section>
        <ds-section heading="Controls" lead="Band and menu controls: company switcher, account menu, theme, language and log out.">
          <div class="ds-stack">
            ${gallery('controls', 'band')}
            ${gallery('controls', 'sheet')}
          </div>
        </ds-section>
      </div>
    `,
    components: [NavIcon],
    props: { sections, controls },
  }),
  parameters: {
    docs: {
      description: {
        component:
          'The line icons used by the app band, bottom bar and menus, drawn at 20 px with a 2 px stroke in `currentColor` (the chevron is 16 px, switch-company 18 px, user 22 px). ' +
          'They take the colour of their text, so on the band they are white and on a sheet they follow the ink. ' +
          'They are decorative and always sit next to a visible label or an `aria-label`. ' +
          'On desktop the band drops these icons first when space runs out, so a label must always make sense on its own.',
      },
    },
  },
};

export default meta;

export const Gallery: Story = {};

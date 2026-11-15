import type { Meta, Story } from '../support/story';
import { template } from '../support/story';

const meta: Meta = {
  title: 'Foundations/Layout and breakpoints',
  parameters: {
    docs: {
      description: {
        component:
          'Breakpoints are written in range notation (stylelint enforces it) and the shared responsive mechanics live once in `lets-peppol.css`. ' +
          'Resize the window to pass the reference sizes; the indicator below shows which media queries match right now. ' +
          'Everything is left-aligned and numbers are right-aligned; only empty states and the bottom bar are centred.',
      },
    },
  },
};

export default meta;

export const Breakpoints: Story = {
  render: template(`
    <div class="ds-page">
      <ds-section heading="Live breakpoints" lead="Resize the window and watch which queries match.">
        <ds-breakpoints></ds-breakpoints>
      </ds-section>
      <ds-section heading="What changes where">
        <div class="card table-card">
          <div class="table-wrapper">
            <table class="data-table stack-on-phone">
              <thead><tr><th>Range</th><th>Shell</th><th>Tables</th><th>Editors and modals</th></tr></thead>
              <tbody>
                <tr><td data-primary>Desktop, 1024px and up</td><td data-label="Shell">Navy band 56px with nav labels; it folds secondary items into the account menu when it would wrap.</td><td data-label="Tables">Real table.</td><td data-label="Editors and modals">Actions in the header; modals centred.</td></tr>
                <tr><td data-primary>Tablet, 768–1023px</td><td data-label="Shell">Band keeps labels; utilities fold into the account menu; sidebars become a segmented row.</td><td data-label="Tables">Real table; low-priority columns marked .tablet-hide disappear.</td><td data-label="Editors and modals">Sticky bottom action bar; filters become a 2×2 grid.</td></tr>
                <tr><td data-primary>Phone portrait, under 768px</td><td data-label="Shell">Compact band 52px plus a bottom bar with at most five slots.</td><td data-label="Tables">.stack-on-phone tables become row cards.</td><td data-label="Editors and modals">Modals become bottom sheets; primary action full width.</td></tr>
                <tr><td data-primary>Phone landscape, height 500px or less</td><td data-label="Shell">Band 44px, no bottom bar; the menu is a left drawer.</td><td data-label="Tables">As phone.</td><td data-label="Editors and modals">As phone.</td></tr>
              </tbody>
            </table>
          </div>
        </div>
      </ds-section>
    </div>
  `),
};

export const Grids: Story = {
  render: template(`
    <div class="ds-page">
      <ds-section heading="Grid" lead=".grid is five columns on desktop, two on tablet and one on phones. Fields use .field.full to span the row.">
        <div class="grid">
          <div class="ds-shape" style="border-radius: var(--radius); height: 3rem">1</div>
          <div class="ds-shape" style="border-radius: var(--radius); height: 3rem">2</div>
          <div class="ds-shape" style="border-radius: var(--radius); height: 3rem">3</div>
          <div class="ds-shape" style="border-radius: var(--radius); height: 3rem">4</div>
          <div class="ds-shape" style="border-radius: var(--radius); height: 3rem">5</div>
        </div>
      </ds-section>
      <ds-section heading="Container" lead="main.container holds every page: max 1460px plus gutters, 24px top padding (16px on phones).">
        <div class="ds-frame">
          <div class="container" style="margin: 0; padding-top: var(--space-4); outline: 1px dashed var(--orange)">
            <div class="card">Page content sits inside the container gutters.</div>
          </div>
        </div>
      </ds-section>
      <ds-section heading="Rules">
        <div class="ds-stack">
          <ds-rule kind="do">Use container queries (<code>inline-size</code>) for components embedded at different widths, such as tiles in the invoice editor or cards in settings, instead of guessing from the viewport.</ds-rule>
          <ds-rule kind="do">Use <code>dvh</code> for viewport height.</ds-rule>
          <ds-rule kind="dont">Use <code>vh</code> or <code>height: 100vh</code>, fixed min-widths, or page-specific copies of shared mechanics in the global stylesheet.</ds-rule>
        </div>
      </ds-section>
    </div>
  `),
};

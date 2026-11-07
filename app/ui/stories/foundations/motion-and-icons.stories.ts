import type { Meta, Story } from '../support/story';
import { template } from '../support/story';
import { icon } from '../primitives/icons';

const meta: Meta = {
  title: 'Foundations/Motion and icons',
  parameters: {
    docs: {
      description: {
        component:
          'Motion only answers something the person did: a drawer slides in, a bottom sheet rises, a row expands, a toast arrives. 160–220ms, ease-out, nothing on page load, and everything is disabled under `prefers-reduced-motion: reduce`. ' +
          'Icons are inline Lucide-style outline SVGs (24px grid, 2px stroke, round caps) drawn in `currentColor`, so they take the colour of the text around them.',
      },
    },
  },
};

export default meta;

export const Motion: Story = {
  render: () => ({
    template: `
      <div class="ds-page">
        <ds-section heading="Durations" lead="Hover the controls: colour transitions take 160ms. Open the sheet on a phone viewport to see the 220ms rise.">
          <div class="ds-row">
            <button type="button" class="btn">Hover me</button>
            <button type="button" class="btn ghost">Hover me</button>
            <label class="switch"><input type="checkbox" aria-label="Example switch" /><span class="slider"></span></label>
            <button type="button" class="btn primary" click.trigger="open = true">Open a sheet</button>
          </div>
        </ds-section>
        <ds-section heading="Progress" lead="Indeterminate progress loops; under reduced motion the bar fills and the spinner stops.">
          <div class="card table-card" style="height: 3rem"><div class="table-loading-bar"></div></div>
          <div class="spinner"></div>
        </ds-section>
        <div class="modal" role="dialog" aria-modal="true" aria-labelledby="motion-sheet-title" if.bind="open">
          <div class="modal-backdrop" click.trigger="open = false"></div>
          <div class="modal-card">
            <header class="modal-header"><h2 id="motion-sheet-title">A sheet</h2></header>
            <div class="modal-body"><p>On phones this card rises 24px while it fades in.</p></div>
            <footer class="modal-footer"><button type="button" class="btn primary" click.trigger="open = false">Close</button></footer>
          </div>
        </div>
      </div>
    `,
    props: { open: false },
  }),
};

export const Icons: Story = {
  render: template(`
    <div class="ds-page">
      <ds-section heading="Sizes" lead="18px inside buttons and table actions, 20px in notices and toasts, 32px in empty states. Navigation icons live in Components/Nav icons.">
        <div class="ds-row" style="gap: var(--space-6); align-items: end">
          <div class="ds-stack" style="justify-items: center">${icon.send(16)}<span class="ds-label">16 small button</span></div>
          <div class="ds-stack" style="justify-items: center">${icon.send(18)}<span class="ds-label">18 button</span></div>
          <div class="ds-stack" style="justify-items: center">${icon.info(20)}<span class="ds-label">20 notice</span></div>
          <div class="ds-stack" style="justify-items: center">${icon.inbox(32)}<span class="ds-label">32 empty state</span></div>
        </div>
      </ds-section>
      <ds-section heading="Colour follows context" lead="Icons inherit currentColor: muted in icon buttons, the accent in notices, white on navy.">
        <div class="ds-row">
          <button type="button" class="icon-btn" aria-label="Edit">${icon.pencil()}</button>
          <span style="color: var(--warn)">${icon.warning(20)}</span>
          <span style="color: var(--ok)">${icon.ok(20)}</span>
          <span style="display: inline-flex; padding: var(--space-2); border-radius: var(--radius); background: var(--band); color: var(--on-navy)">${icon.users(20)}</span>
          <button type="button" class="btn primary">${icon.plus()}<span>New invoice</span></button>
        </div>
      </ds-section>
      <ds-section heading="Rules">
        <div class="ds-stack">
          <ds-rule kind="do">Mark decorative icons <code>aria-hidden="true"</code>; give icon-only buttons an <code>aria-label</code>.</ds-rule>
          <ds-rule kind="do">Pair an icon with a label wherever there is room. Icons alone are for well-known actions: close, delete, edit, download.</ds-rule>
          <ds-rule kind="note">Icons are pasted inline today, with stroke widths of both 1.5 and 2. A shared icon element (as <code>nav-icon</code> already is for navigation) would keep them consistent.</ds-rule>
        </div>
      </ds-section>
    </div>
  `),
};

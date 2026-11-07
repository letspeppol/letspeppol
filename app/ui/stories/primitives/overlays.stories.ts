import type { Meta, Story } from '../support/story';
import { template } from '../support/story';
import { icon } from './icons';

const modal = `
  <div class="modal" role="dialog" aria-modal="true" aria-labelledby="sb-modal-title">
    <div class="modal-backdrop"></div>
    <div class="modal-card">
      <header class="modal-header">
        <h2 id="sb-modal-title">Payment terms</h2>
        <button type="button" class="icon-btn" aria-label="Close">${icon.close()}</button>
      </header>
      <div class="modal-body">
        <div class="grid" style="grid-template-columns: 1fr">
          <label class="field required"><span>Due date</span><input type="date" value="2026-12-05" /></label>
          <label class="field"><span>Payment reference</span><input type="text" class="tabular" value="+++090/9337/55493+++" /></label>
        </div>
      </div>
      <footer class="modal-footer">
        <span class="modal-footer-info">Changes apply to this invoice only.</span>
        <button type="button" class="btn">Cancel</button>
        <button type="button" class="btn primary">Save changes</button>
      </footer>
    </div>
  </div>
`;

const editor = `
  <main class="container">
    <div class="section-header">
      <h1>INV-2026-0142</h1>
      <div class="action-bar">
        <button type="button" class="btn">Save draft</button>
        <button type="button" class="btn primary">${icon.send()}<span>Send invoice</span></button>
      </div>
    </div>
    <div class="card"><p style="margin: 0">Invoice content</p></div>
  </main>
`;

const meta: Meta = {
  title: 'Primitives/Modals and action bar',
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 520 },
      description: {
        component:
          '`.modal` is centred on desktop and becomes a bottom sheet under 768px: full width, max 90dvh, rounded top corners, footer buttons stacked full width with the primary on top. ' +
          'Dialogs carry `role="dialog"`, `aria-modal`, a labelled title and a focus trap. ' +
          'In editors, `.action-bar` holds save/send: inline in the header on desktop, a sticky bar above the bottom bar under 1024px.',
      },
    },
  },
};

export default meta;

export const ModalDesktop: Story = {
  name: 'Modal on desktop',
  render: template(modal),
  globals: { viewport: { value: 'desktop', isRotated: false } },
};

export const ModalPhone: Story = {
  name: 'Bottom sheet on a phone',
  render: template(modal),
  globals: { viewport: { value: 'phone', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 844 } } },
};

export const ActionBarDesktop: Story = {
  name: 'Action bar on desktop',
  render: template(editor),
  globals: { viewport: { value: 'desktop', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 260 } } },
};

export const ActionBarTablet: Story = {
  name: 'Action bar on a tablet',
  render: template(editor),
  globals: { viewport: { value: 'tabletPortrait', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 600 } } },
};

export const ActionBarPhone: Story = {
  name: 'Action bar on a phone',
  render: template(editor),
  globals: { viewport: { value: 'phone', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 844 } } },
};

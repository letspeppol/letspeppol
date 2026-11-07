import type { Meta, Story } from '../support/story';
import { template } from '../support/story';
import { icon } from './icons';

const meta: Meta = {
  title: 'Primitives/Page structure',
  parameters: {
    docs: {
      description: {
        component:
          'Every signed-in page starts with `.section-header` (one h1 and its actions), then an optional segmented row, then flat sheets (`.card`). ' +
          'The segmented control switches views within a page; the active segment gets the orange underline. ' +
          'Audit: `.section-header` 12 templates, `.segmented` 4, `.card` 10; `.card-header` and `.grid-col-73` are defined globally but unused.',
      },
    },
  },
};

export default meta;

export const SectionHeader: Story = {
  name: 'Page header',
  render: template(`
    <div class="ds-stack" style="gap: var(--space-8)">
      <div class="section-header" style="margin: 0">
        <h1>Partners</h1>
        <div class="actions"><button type="button" class="btn primary">${icon.plus()}<span>New partner</span></button></div>
      </div>
      <div class="section-header" style="margin: 0">
        <h1>INV-2026-0142</h1>
        <div class="editor-header-actions">
          <button type="button" class="btn ghost">Discard</button>
          <button type="button" class="btn">Save draft</button>
          <button type="button" class="btn primary">${icon.send()}<span>Send invoice</span></button>
        </div>
      </div>
    </div>
  `),
};

export const Segmented: Story = {
  name: 'Segmented control',
  render: template(`
    <div class="ds-stack" style="gap: var(--space-6)">
      <nav class="segmented" aria-label="Invoices">
        <button type="button" class="segment active" aria-pressed="true"><span>All</span><span class="badge">148</span></button>
        <button type="button" class="segment" aria-pressed="false"><span>Outgoing</span><span class="badge">96</span></button>
        <button type="button" class="segment" aria-pressed="false"><span>Incoming</span><span class="badge">41</span></button>
        <button type="button" class="segment" aria-pressed="false"><span>Drafts</span><span class="badge">11</span></button>
        <button type="button" class="segment" aria-pressed="false"><span>Credit notes</span></button>
        <button type="button" class="segment" aria-pressed="false"><span>Self-billing</span></button>
      </nav>
      <nav class="segmented" aria-label="Settings">
        <a class="segment" aria-current="page" href="#">Company</a>
        <a class="segment" href="#">Users</a>
      </nav>
    </div>
  `),
  parameters: {
    docs: {
      description: {
        story: 'On phones the row scrolls horizontally in one line with scroll snapping instead of wrapping.',
      },
    },
  },
};

export const SegmentedPhone: Story = {
  name: 'Segmented control on a phone',
  render: Segmented.render,
  globals: { viewport: { value: 'smallPhone', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 220 } } },
};

export const Cards: Story = {
  render: template(`
    <div class="ds-grid wide">
      <section class="card">
        <h3 style="margin-top: 0">Payment</h3>
        <p class="muted" style="margin: 0">30 days after the invoice date, to BE68 5390 0754 7034.</p>
      </section>
      <section class="card">
        <h3 style="margin-top: 0">Customer</h3>
        <p style="margin: 0">Brasserie Het Anker NV<br><span class="muted">Guido Gezellelaan 49, 2800 Mechelen</span></p>
      </section>
    </div>
  `),
  parameters: {
    docs: {
      description: {
        story: 'Cards are flat sheets: 1px rule, 6px radius, 16px padding, no shadow. Don’t nest them.',
      },
    },
  },
};

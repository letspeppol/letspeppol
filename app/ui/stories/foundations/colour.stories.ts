import type { Meta, Story } from '../support/story';
import { template } from '../support/story';

const meta: Meta = {
  title: 'Foundations/Colour',
  parameters: {
    docs: {
      description: {
        component:
          'Two ideas from the logo carry the whole palette: navy is the institution, orange is the check mark. ' +
          'Everything else is a quiet paper ground with flat white sheets. Tokens live in `src/app/lets-peppol.css`; ' +
          'use the semantic names, never raw hex. Every swatch reads its value live, so switch the Theme toolbar to compare light and dark.',
      },
    },
  },
};

export default meta;

export const Brand: Story = {
  render: template(`
    <div class="ds-page">
      <ds-section heading="Brand" lead="Navy is the brand ink and the band. Orange marks the one place on a screen that needs attention.">
        <div class="ds-grid">
          <ds-swatch token="--band" on="--on-navy" usage="The navy band, the auth panel and strip. Identical in both themes so the band never glares."></ds-swatch>
          <ds-swatch token="--navy" on="--on-navy" usage="Primary buttons, links, checked controls, active states."></ds-swatch>
          <ds-swatch token="--navy-deep" on="--on-navy" usage="Hover on navy."></ds-swatch>
          <ds-swatch token="--navy-ink" on="--sheet" usage="Navy used as text on a sheet: links, ghost buttons, the active segment."></ds-swatch>
          <ds-swatch token="--orange" usage="The check: active marker, in-flight status, attention. Never text, never decoration."></ds-swatch>
          <ds-swatch token="--orange-ink" on="--sheet" usage="Orange as text on a sheet, for AA contrast."></ds-swatch>
        </div>
      </ds-section>
    </div>
  `),
};

export const Surfaces: Story = {
  render: template(`
    <div class="ds-page">
      <ds-section heading="Surfaces and rules" lead="Content sits on flat sheets on a cool paper ground. Sheets have a 1px rule and no shadow.">
        <div class="ds-grid">
          <ds-swatch token="--ground" usage="Page background, the paper on the counter. Also the fill of read-only inputs."></ds-swatch>
          <ds-swatch token="--sheet" usage="Cards, tables, forms, modals, menus."></ds-swatch>
          <ds-swatch token="--rule" usage="Sheet borders, table head rule."></ds-swatch>
          <ds-swatch token="--rule-soft" usage="Row dividers, dashed rules, neutral pill fill."></ds-swatch>
          <ds-swatch token="--rule-strong" usage="Control borders (inputs, selects, default buttons) and the switch track."></ds-swatch>
          <ds-swatch token="--scrim" usage="Backdrop behind modals, drawers and bottom sheets."></ds-swatch>
        </div>
      </ds-section>
    </div>
  `),
};

export const Text: Story = {
  render: template(`
    <div class="ds-page">
      <ds-section heading="Text" lead="Three steps of ink. Contrast is measured against the sheet.">
        <div class="ds-grid">
          <ds-swatch token="--ink" on="--sheet" usage="Headings, strong text, control text, totals."></ds-swatch>
          <ds-swatch token="--text" on="--sheet" usage="Body copy and table cells."></ds-swatch>
          <ds-swatch token="--muted" on="--sheet" usage="Labels, hints, table heads, meta. Not for anything the user must read to act."></ds-swatch>
        </div>
        <div class="ds-grid wide">
          <ds-contrast foreground="--muted" background="--ground" label="Muted on the ground"></ds-contrast>
          <ds-contrast foreground="--navy-ink" background="--sheet" label="Link on a sheet"></ds-contrast>
          <ds-contrast foreground="--orange-ink" background="--sheet" label="Orange ink on a sheet"></ds-contrast>
          <ds-contrast foreground="--orange" background="--sheet" label="Raw orange on a sheet: why we don’t"></ds-contrast>
        </div>
      </ds-section>
    </div>
  `),
};

export const Status: Story = {
  render: template(`
    <div class="ds-page">
      <ds-section heading="Status" lead="Each status has a strong colour for text and icons and a tint for fills. The swatch shows the strong colour on its own tint.">
        <div class="ds-grid">
          <ds-swatch token="--ok-tint" on="--ok" usage="--ok: paid, delivered, valid, enabled."></ds-swatch>
          <ds-swatch token="--warn-tint" on="--warn" usage="--warn: open, scheduled, pending, needs attention soon."></ds-swatch>
          <ds-swatch token="--danger-tint" on="--danger" usage="--danger: overdue, error, destructive. --danger-fill is the solid button."></ds-swatch>
          <ds-swatch token="--info-tint" on="--info" usage="--info: sent, incoming, neutral information."></ds-swatch>
          <ds-swatch token="--danger-fill" on="--on-navy" usage="Solid destructive button fill."></ds-swatch>
        </div>
        <div class="ds-row">
          <span class="pill paid">Paid</span>
          <span class="pill scheduled">Scheduled</span>
          <span class="pill overdue">Overdue</span>
          <span class="pill sent">Sent</span>
          <span class="pill">Draft</span>
        </div>
      </ds-section>
    </div>
  `),
};

export const Interaction: Story = {
  render: template(`
    <div class="ds-page">
      <ds-section heading="Interaction" lead="States are overlays on whatever surface they sit on, so they work on the sheet and the ground alike.">
        <div class="ds-grid">
          <ds-swatch token="--focus" usage="The 2px focus ring with 2px offset, on everything focusable."></ds-swatch>
          <ds-swatch token="--hover" usage="Hover wash on rows, ghost and default buttons, menu rows."></ds-swatch>
          <ds-swatch token="--selected" usage="Pressed and selected wash."></ds-swatch>
        </div>
        <div class="ds-row">
          <button type="button" class="btn">Default</button>
          <button type="button" class="btn" style="background: var(--hover)">Hover</button>
          <button type="button" class="btn" style="background: var(--selected)">Pressed</button>
          <button type="button" class="btn" style="outline: 2px solid var(--focus); outline-offset: 2px">Focus</button>
        </div>
      </ds-section>
    </div>
  `),
};

export const Rules: Story = {
  render: template(`
    <div class="ds-page">
      <ds-section heading="Rules">
        <div class="ds-stack">
          <ds-rule kind="do">Use orange once per screen, for the thing that needs the person’s attention: the active section, an invoice in flight, an action that is waiting.</ds-rule>
          <ds-rule kind="dont">Use orange as decoration, for headings, or as text. Orange text uses <code>--orange-ink</code>.</ds-rule>
          <ds-rule kind="do">Pair every status colour with words or an icon. Colour is never the only signal.</ds-rule>
          <ds-rule kind="dont">Write raw hex in component CSS. Legacy names such as <code>--primary</code>, <code>--panel</code> and <code>--status-*</code> are aliases kept for old CSS; new CSS uses the semantic names.</ds-rule>
          <ds-rule kind="dont">Add gradients, glass blur or radial washes on the ground.</ds-rule>
        </div>
      </ds-section>
    </div>
  `),
};

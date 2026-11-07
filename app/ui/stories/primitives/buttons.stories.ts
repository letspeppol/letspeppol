import type { Meta, Story } from '../support/story';
import { template } from '../support/story';
import { icon } from './icons';

const meta: Meta = {
  title: 'Primitives/Buttons',
  parameters: {
    docs: {
      description: {
        component:
          '`.btn` is the only button. `.primary` (navy) is the one main action of a view, the default is a quiet outline, `.ghost` is for low-emphasis and pager actions, ' +
          '`.danger` is only for confirming something destructive. `.small` exists for dense rows. Icon-only actions use `.icon-btn` and always carry an `aria-label`. ' +
          'Under `(pointer: coarse)` every button grows to the 44px touch target. ' +
          'Audit: `.btn` 137 uses, `.icon-btn` 31 templates, `.btn.small` 4, `.btn.ghost` 5, `.btn.danger` 2.',
      },
    },
  },
};

export default meta;

export const Variants: Story = {
  render: template(`
    <div class="ds-page">
      <ds-section heading="Variants" lead="One primary per view. Labels are verbs that name the result.">
        <div class="ds-row">
          <button type="button" class="btn primary">${icon.send()}<span>Send invoice</span></button>
          <button type="button" class="btn">Save draft</button>
          <button type="button" class="btn ghost">Cancel</button>
          <button type="button" class="btn danger">${icon.trash()}<span>Delete partner</span></button>
        </div>
      </ds-section>
      <ds-section heading="Small" lead="Dense rows only, such as a table toolbar or a tile header.">
        <div class="ds-row">
          <button type="button" class="btn primary small">${icon.plus(16)}<span>Add line</span></button>
          <button type="button" class="btn small">${icon.download(16)}<span>Export</span></button>
          <button type="button" class="btn ghost small">Show all</button>
        </div>
      </ds-section>
      <ds-section heading="Disabled" lead="Disabled buttons drop to 55% opacity. Prefer hiding actions the person has no permission for; disable only when the action becomes available after something else.">
        <div class="ds-row">
          <button type="button" class="btn primary" disabled>Send invoice</button>
          <button type="button" class="btn" disabled>Save draft</button>
          <button type="button" class="btn ghost" disabled>Previous</button>
          <button type="button" class="btn danger" disabled>Delete</button>
        </div>
      </ds-section>
    </div>
  `),
};

export const IconButtons: Story = {
  name: 'Icon buttons',
  render: template(`
    <div class="ds-page">
      <ds-section heading="Icon button" lead=".icon-btn is borderless and muted until hovered. .success turns it green for confirm-in-place actions.">
        <div class="ds-row">
          <button type="button" class="icon-btn" aria-label="Edit">${icon.pencil()}</button>
          <button type="button" class="icon-btn" aria-label="Download">${icon.download()}</button>
          <button type="button" class="icon-btn" aria-label="Delete">${icon.trash()}</button>
          <button type="button" class="icon-btn success" aria-label="Mark as paid">${icon.check()}</button>
          <button type="button" class="icon-btn" aria-label="Close">${icon.close()}</button>
          <button type="button" class="icon-btn" aria-label="Delete" disabled>${icon.trash()}</button>
        </div>
      </ds-section>
      <ds-section heading="Row actions" lead=".row-actions .icon gives table actions a visible outline so they read as buttons inside dense rows.">
        <div class="row-actions">
          <button type="button" class="icon" aria-label="Edit">${icon.pencil()}</button>
          <button type="button" class="icon" aria-label="Download">${icon.download()}</button>
          <button type="button" class="icon" aria-label="Delete">${icon.trash()}</button>
        </div>
      </ds-section>
    </div>
  `),
};

export const InContext: Story = {
  name: 'In a page header',
  render: template(`
    <div class="section-header">
      <h1>Invoices</h1>
      <div class="actions">
        <button type="button" class="btn">${icon.file()}<span>Upload UBL</span></button>
        <button type="button" class="btn primary">${icon.plus()}<span>New invoice</span></button>
      </div>
    </div>
  `),
  parameters: {
    docs: {
      description: {
        story: 'Under 768px the header stacks and, under 480px, the primary action takes the full width.',
      },
    },
  },
};

export const Touch: Story = {
  name: 'Phone',
  render: InContext.render,
  globals: { viewport: { value: 'phone', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 260 } } },
};

export const Rules: Story = {
  render: template(`
    <div class="ds-stack">
      <ds-rule kind="do">Name the result: “Send invoice”, “Save changes”, “Invite user”. The toast that follows uses the same word: “Invoice sent”.</ds-rule>
      <ds-rule kind="do">Keep one <code>.primary</code> per view. In modals the primary sits last in the footer.</ds-rule>
      <ds-rule kind="dont">Append arrows to labels, or write “Click here”.</ds-rule>
      <ds-rule kind="dont">Use <code>.danger</code> to start a destructive flow; use it in the confirmation that follows.</ds-rule>
      <ds-rule kind="dont">Ship an icon-only button without an <code>aria-label</code>.</ds-rule>
    </div>
  `),
};

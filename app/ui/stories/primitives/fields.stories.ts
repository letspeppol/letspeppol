import type { Meta, Story } from '../support/story';
import { template } from '../support/story';

const meta: Meta = {
  title: 'Primitives/Fields',
  parameters: {
    docs: {
      description: {
        component:
          '`.field` is a label wrapping a muted caption and one control, stacked with a 4px gap. `.required` adds a red asterisk; read-only and disabled controls sit on the ground colour. ' +
          'Lay fields out in `.grid` (5 → 2 → 1 columns) and span the row with `.full`. Validation messages use `.form-error` directly under the field or the form. ' +
          'Audit: `.field` 101 uses; `.form-error` 13 templates; `.span-2` is defined globally but unused (pages ship their own `settings-span-2` and `partner-span-2`).',
      },
    },
  },
};

export default meta;

export const TextInputs: Story = {
  name: 'Text, select and textarea',
  render: template(`
    <div class="card">
      <div class="grid">
        <label class="field required"><span>Company name</span><input type="text" value="Brasserie Het Anker NV" /></label>
        <label class="field"><span>VAT number</span><input type="text" class="tabular" value="BE0401234567" /></label>
        <label class="field"><span>Email</span><input type="email" placeholder="facturen@example.be" /></label>
        <label class="field"><span>Payment terms</span>
          <select><option>14 days</option><option selected>30 days</option><option>End of month</option></select>
        </label>
        <label class="field"><span>Due date</span><input type="date" value="2026-12-05" /></label>
        <label class="field full"><span>Note to customer</span><textarea>Thank you for your order. Delivery on Friday morning.</textarea></label>
      </div>
    </div>
  `),
};

export const States: Story = {
  render: template(`
    <div class="card">
      <div class="grid">
        <label class="field"><span>Read-only</span><input type="text" value="0208:0401234567" readonly /></label>
        <label class="field"><span>Disabled</span><input type="text" value="Not editable" disabled /></label>
        <label class="field required missing-required"><span>VAT rate</span>
          <select><option value="">Choose a rate</option><option>21%</option><option>6%</option></select>
        </label>
        <label class="field required"><span>IBAN</span><input type="text" class="tabular" value="BE68 5390 0754 703" aria-invalid="true" /></label>
      </div>
      <p class="form-error" role="alert" style="margin-top: var(--space-4)">This IBAN is one digit short. Check the number on your bank statement.</p>
    </div>
  `),
  parameters: {
    docs: {
      description: {
        story: '`.missing-required` only styles selects today. Text inputs have no invalid style of their own; the message carries the error. Worth deciding whether inputs should get the same red outline.',
      },
    },
  },
};

export const ChoiceControls: Story = {
  name: 'Checkboxes, radios and switches',
  render: template(`
    <div class="card ds-stack">
      <label class="field checkbox"><input type="checkbox" checked /><span>Send me an email when an invoice arrives</span></label>
      <label class="field checkbox"><input type="checkbox" /><span>Attach the PDF to outgoing invoices</span></label>
      <label class="field radio"><input type="radio" name="kind" checked /><span>Customer</span></label>
      <label class="field radio"><input type="radio" name="kind" /><span>Supplier</span></label>
      <div class="display-toggle">
        <span class="vat-toggle-label">Excl. VAT</span>
        <label class="switch"><input type="checkbox" checked aria-label="Show amounts including VAT" /><span class="slider"></span></label>
        <span class="vat-toggle-label active">Incl. VAT</span>
      </div>
      <div class="display-toggle">
        <label class="switch"><input type="checkbox" aria-label="Receive documents" /><span class="slider"></span></label>
        <span>Receive documents on Peppol</span>
      </div>
    </div>
  `),
  parameters: {
    docs: {
      description: {
        story: 'The switch is for settings that take effect immediately. The VAT display toggle labels both sides and bolds the active one.',
      },
    },
  },
};

export const Filters: Story = {
  name: 'Search and filters',
  render: template(`
    <div class="filters toolbar-filters">
      <label class="search">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M21 21l-4.35-4.35" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="1.5"/></svg>
        <input type="search" placeholder="Search partner" aria-label="Search partner" />
      </label>
      <label class="select"><span>Status</span>
        <select><option>All statuses</option><option>Open</option><option>Overdue</option></select>
      </label>
      <label class="select"><span>From</span><input type="date" value="2026-10-01" /></label>
      <label class="select"><span>To</span><input type="date" value="2026-10-31" /></label>
    </div>
  `),
  parameters: {
    docs: {
      description: {
        story: 'Filter rows stay fully visible: under 1024px they become a two-column grid, and in phone portrait each search field takes a full row (page CSS implements this per screen).',
      },
    },
  },
};

export const Rules: Story = {
  render: template(`
    <div class="ds-stack">
      <ds-rule kind="do">Label every control with visible text. Placeholders show an example, never the label.</ds-rule>
      <ds-rule kind="do">Say what went wrong and what to do next: “This IBAN is one digit short. Check the number on your bank statement.”</ds-rule>
      <ds-rule kind="dont">End labels with a colon, or write them in capitals.</ds-rule>
      <ds-rule kind="dont">Disable a field to show a value; make it read-only so it stays selectable and readable.</ds-rule>
    </div>
  `),
};

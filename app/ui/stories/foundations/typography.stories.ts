import type { Meta, Story } from '../support/story';
import { template } from '../support/story';

const meta: Meta = {
  title: 'Foundations/Typography',
  parameters: {
    docs: {
      description: {
        component:
          'One family, Instrument Sans (variable 400–700), self-hosted in `public/fonts` because production CSP only allows `font-src \'self\'`. ' +
          'Sizes step down from display totals to micro sublines; headings and page titles shrink under 768px. ' +
          'Every amount, date, reference and id uses tabular figures so columns line up.',
      },
    },
  },
};

export default meta;

export const Scale: Story = {
  render: template(`
    <div class="ds-page">
      <ds-section heading="Type scale" lead="Narrow the window to phone width to see the display and page title step down.">
        <div class="card">
          <div class="ds-specimen">
            <div class="ds-specimen-meta"><strong>Display</strong><span>2rem / 1.15 / 700</span><span>.display, ledger totals, wizard titles</span></div>
            <div class="display tabular">€ 12.480,50</div>
          </div>
          <div class="ds-specimen">
            <div class="ds-specimen-meta"><strong>Page title</strong><span>1.5rem / 1.2 / 700</span><span>h1, one per page</span></div>
            <h1 style="margin: 0">Invoices</h1>
          </div>
          <div class="ds-specimen">
            <div class="ds-specimen-meta"><strong>Section</strong><span>1.125rem / 1.3 / 650</span><span>h2</span></div>
            <h2 style="margin: 0">Payment details</h2>
          </div>
          <div class="ds-specimen">
            <div class="ds-specimen-meta"><strong>Card title</strong><span>1rem / 1.35 / 600</span><span>h3</span></div>
            <h3 style="margin: 0">Customer</h3>
          </div>
          <div class="ds-specimen">
            <div class="ds-specimen-meta"><strong>Body</strong><span>1rem / 1.5 / 400</span><span>p, capped at 65ch</span></div>
            <p style="margin: 0">Send your first invoice over Peppol in a few minutes. Your customer receives it directly in their accounting software, and you see when it arrives.</p>
          </div>
          <div class="ds-specimen">
            <div class="ds-specimen-meta"><strong>Table body</strong><span>0.9375rem / 1.5</span><span>.data-table cells, notices, buttons</span></div>
            <span style="font-size: .9375rem">Brasserie Het Anker NV</span>
          </div>
          <div class="ds-specimen">
            <div class="ds-specimen-meta"><strong>Small</strong><span>0.8125rem / 1.4</span><span>labels, hints, table heads, pills</span></div>
            <small>Due in 14 days</small>
          </div>
          <div class="ds-specimen">
            <div class="ds-specimen-meta"><strong>Micro</strong><span>0.75rem / 1.3</span><span>column sublines and counters only</span></div>
            <span style="font-size: var(--text-xs); color: var(--muted)">BE0401234567</span>
          </div>
        </div>
      </ds-section>
    </div>
  `),
};

export const TabularFigures: Story = {
  name: 'Tabular figures',
  render: template(`
    <div class="ds-page">
      <ds-section heading="Tabular figures" lead="Money, dates, references and ids use .num or .tabular. Amounts are right-aligned; totals are weight 700.">
        <div class="ds-grid wide">
          <div class="card">
            <p class="ds-label">Proportional (wrong)</p>
            <div style="text-align: right; font-variant-numeric: proportional-nums">
              <div>€ 1.111,11</div><div>€ 888,80</div><div>€ 10.070,19</div><div style="font-weight: 700">€ 12.070,10</div>
            </div>
          </div>
          <div class="card">
            <p class="ds-label">Tabular (right)</p>
            <div class="num" style="text-align: right">
              <div>€ 1.111,11</div><div>€ 888,80</div><div>€ 10.070,19</div><div style="font-weight: 700">€ 12.070,10</div>
            </div>
          </div>
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
          <ds-rule kind="do">Write in sentence case everywhere, including buttons, table heads and page titles.</ds-rule>
          <ds-rule kind="dont">Use all-caps labels, letter-spaced eyebrows or trailing colons on labels.</ds-rule>
          <ds-rule kind="dont">Accent a single word in a heading with another colour or weight.</ds-rule>
          <ds-rule kind="dont">Use monospace for small data labels; tabular figures in the body face do that job.</ds-rule>
          <ds-rule kind="note">Paragraphs and hints stop at 65ch so lines stay readable on wide screens.</ds-rule>
        </div>
      </ds-section>
    </div>
  `),
};

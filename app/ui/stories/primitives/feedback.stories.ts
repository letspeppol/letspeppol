import type { Meta, Story } from '../support/story';
import { template } from '../support/story';
import { icon } from './icons';

const meta: Meta = {
  title: 'Primitives/Status and feedback',
  parameters: {
    docs: {
      description: {
        component:
          'Pills name a status in words on a tint of its colour; badges count things. Notices are inline messages inside a sheet; `.form-error` is the inline validation message. ' +
          'Transient confirmations are toasts (see Components/Toasts). Colour is never the only signal: every pill has text, every notice an icon. ' +
          'Audit: `.pill` 5 templates, `.badge` 3, `.form-error` 13. The global `.notice` has no users: settings and the wizard ship their own `settings-notice` and `wizard-notice`, which is a candidate for consolidation.',
      },
    },
  },
};

export default meta;

export const Pills: Story = {
  name: 'Pills and badges',
  render: template(`
    <div class="ds-page">
      <ds-section heading="Status pills" lead="One pill per status. The class names follow the domain: sent/incoming, scheduled, processed/paid, error/overdue.">
        <div class="ds-row">
          <span class="pill">Draft</span>
          <span class="pill sent">Sent</span>
          <span class="pill incoming">Incoming</span>
          <span class="pill scheduled">Scheduled</span>
          <span class="pill processed">Processed</span>
          <span class="pill paid">Paid</span>
          <span class="pill overdue">Overdue</span>
          <span class="pill error">Error</span>
        </div>
      </ds-section>
      <ds-section heading="Badges" lead="Counters next to a label, such as filter segments. Tabular figures, never a status.">
        <div class="ds-row">
          <span>Customers <span class="badge">12</span></span>
          <span>Suppliers <span class="badge">3</span></span>
          <span>All <span class="badge">148</span></span>
        </div>
      </ds-section>
    </div>
  `),
};

export const Notices: Story = {
  render: template(`
    <div class="ds-stack" style="max-width: 48rem">
      <div class="notice info" role="status">${icon.info(20)}<p class="notice-text">Your customer is on Peppol. The invoice arrives directly in their accounting software.</p></div>
      <div class="notice warn" role="status">${icon.warning(20)}<p class="notice-text">Receiving is turned off. Suppliers can’t send you invoices over Peppol until you turn it on.</p><button type="button" class="btn small notice-action">Turn on</button></div>
      <div class="notice danger" role="alert">${icon.danger(20)}<p class="notice-text">This invoice was rejected by the receiver’s access point. Check the VAT number and send it again.</p></div>
      <div class="notice ok" role="status">${icon.ok(20)}<p class="notice-text">Your company is registered on Peppol.</p></div>
      <p class="form-error" role="alert">The invoice number INV-2026-0141 is already used. Choose the next number.</p>
    </div>
  `),
};

export const Loading: Story = {
  name: 'Loading',
  render: template(`
    <div class="ds-page">
      <ds-section heading="Table loading bar" lead="When a list refreshes, the bar slides along the top of the table card and the rows fade to 45%.">
        <div class="card table-card">
          <div class="table-loading-bar" role="progressbar" aria-label="Loading"></div>
          <div class="table-wrapper is-loading">
            <table class="data-table"><thead><tr><th>Partner</th><th class="num">Amount</th></tr></thead>
              <tbody><tr><td>Brasserie Het Anker NV</td><td class="num">€ 1.240,00</td></tr><tr><td>Hotel Le Mosan SA</td><td class="num">€ 318,60</td></tr></tbody>
            </table>
          </div>
        </div>
      </ds-section>
      <ds-section heading="Spinners" lead="The large spinner is for whole-view waits; the empty spinner sits inside an empty state while the first page loads.">
        <div class="ds-row" style="gap: var(--space-8)">
          <div class="spinner" role="progressbar" aria-label="Loading"></div>
          <div class="empty-state"><div class="empty-spinner"></div><p>Loading invoices</p></div>
        </div>
      </ds-section>
    </div>
  `),
};

export const EmptyStates: Story = {
  name: 'Empty states',
  render: template(`
    <div class="ds-grid wide">
      <div class="card">
        <div class="empty-state">
          <div class="empty-icon">${icon.inbox(32)}</div>
          <p>Add your customers and suppliers once, then pick them when you write an invoice.</p>
          <button type="button" class="btn primary">New partner</button>
        </div>
      </div>
      <div class="card">
        <div class="empty-state">
          <p>No partners match your search.</p>
        </div>
      </div>
    </div>
  `),
  parameters: {
    docs: {
      description: {
        story: 'A first-use empty state invites the first action with one line and one button. A filtered-empty state only says nothing matches.',
      },
    },
  },
};

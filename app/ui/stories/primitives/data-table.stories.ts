import type { Meta, Story } from '../support/story';
import { template } from '../support/story';
import { icon } from './icons';

const rows = [
  { doc: 'INV-2026-0141', partner: 'Brasserie Het Anker NV', date: '28/10/2026', due: '27/11/2026', amount: '€\u00a01.240,00', status: 'Paid', pill: 'paid' },
  { doc: 'INV-2026-0140', partner: 'Hotel Le Mosan SA', date: '21/10/2026', due: '04/11/2026', amount: '€\u00a0318,60', status: 'Overdue', pill: 'overdue' },
  { doc: 'INV-2026-0139', partner: 'Koffiebranderij Mokabon – Groothandel in koffie, thee en aanverwante producten', date: '14/10/2026', due: '13/11/2026', amount: '€\u00a012.070,10', status: 'Sent', pill: 'sent' },
  { doc: 'INV-2026-0138', partner: 'Café De Zwaan', date: '02/10/2026', due: '01/11/2026', amount: '€\u00a086,40', status: 'Scheduled', pill: 'scheduled' },
];

const table = `
  <div class="card table-card">
    <div class="table-wrapper">
      <table class="data-table stack-on-phone">
        <thead>
          <tr>
            <th><button type="button" class="sort-button"><span>Document</span><span class="sort-indicator is-idle" aria-hidden="true"></span></button></th>
            <th><button type="button" class="sort-button"><span>Partner</span><span class="sort-indicator is-asc" aria-hidden="true"></span></button></th>
            <th class="tablet-hide"><button type="button" class="sort-button"><span>Issue date</span><span class="sort-indicator is-idle" aria-hidden="true"></span></button></th>
            <th><button type="button" class="sort-button"><span>Due date</span><span class="sort-indicator is-desc" aria-hidden="true"></span></button></th>
            <th class="num">Amount</th>
            <th>Status</th>
            <th class="actions-col"><span class="visually-hidden">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          <tr repeat.for="row of rows">
            <td data-primary class="tabular">\${row.doc}</td>
            <td data-label="Partner">\${row.partner}</td>
            <td class="num tablet-hide" data-label="Issue date">\${row.date}</td>
            <td class="num" data-label="Due date">\${row.due}</td>
            <td class="num" data-label="Amount">\${row.amount}</td>
            <td data-label="Status"><span class="pill \${row.pill}">\${row.status}</span></td>
            <td class="actions-col">
              <span class="row-actions">
                <button type="button" class="icon" aria-label="Download">${icon.download()}</button>
                <button type="button" class="icon" aria-label="Delete">${icon.trash()}</button>
              </span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="table-footer pager">
      <button type="button" class="btn ghost" disabled>Previous</button>
      <span class="pager-range">1–20 / 148</span>
      <button type="button" class="btn ghost">Next</button>
    </div>
  </div>
`;

const meta: Meta = {
  title: 'Primitives/Data table',
  parameters: {
    docs: {
      description: {
        component:
          'Lists are `.data-table` inside a `.card.table-card`. Desktop keeps a real table with sticky, sentence-case heads and right-aligned tabular numbers. ' +
          'Between 768 and 1023px columns marked `.tablet-hide` (on both `th` and `td`) disappear so status and actions never scroll out of view. ' +
          'Under 768px `.stack-on-phone` turns rows into cards: the `data-primary` cell becomes the title, every other cell shows its translated `data-label`, and `.actions-col` moves to the bottom as 44px buttons. ' +
          'Audit: `.data-table` 7 templates, all but one also `.stack-on-phone`; `.row-actions` 1, `.sort-button` 3.',
      },
    },
  },
};

export default meta;

export const Desktop: Story = {
  render: () => ({ template: table, props: { rows } }),
  globals: { viewport: { value: 'desktop', isRotated: false } },
  parameters: {
    docs: {
      story: { inline: false, iframeHeight: 420 },
      description: {
        story:
          'Open question: references such as INV-2026-0141 break at the hyphen when a column gets narrow. There is no shared no-wrap rule, so pages add their own (`partner-nowrap`, `download-date`).',
      },
    },
  },
};

export const Tablet: Story = {
  render: () => ({ template: table, props: { rows } }),
  globals: { viewport: { value: 'tabletPortrait', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 460 } } },
};

export const Phone: Story = {
  name: 'Phone (row cards)',
  render: () => ({ template: table, props: { rows } }),
  globals: { viewport: { value: 'phone', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 900 } } },
};

export const Rules: Story = {
  render: template(`
    <div class="ds-stack">
      <ds-rule kind="do">Set <code>data-label</code> through i18n (<code>t="[data-label]invoice.table.doc"</code>) so phone cards are translated.</ds-rule>
      <ds-rule kind="do">Mark the identifying cell <code>data-primary</code>; it becomes the card title on phones.</ds-rule>
      <ds-rule kind="do">Mark low-priority columns <code>.tablet-hide</code> on both the head and the cells.</ds-rule>
      <ds-rule kind="do">Page a list at twenty rows and show the pager only when there is more than one page.</ds-rule>
      <ds-rule kind="do">Leave out the action column and the “New” button for a person who may not change the list; never show them disabled.</ds-rule>
      <ds-rule kind="dont">Hide the status or actions column at any width.</ds-rule>
    </div>
  `),
};

import type { Meta, Story } from '../support/story';
import { PaymentTermsField } from '../../src/components/payment-terms/payment-terms-field';

const meta: Meta = {
  title: 'Components/Payment terms field',
  render: () => ({
    template: `
      <div class="ds-frame sheet" style="max-width: 32rem">
        <div class="field">
          <span t="label.payment-terms">Payment terms</span>
          <payment-terms-field value.two-way="value" disabled.bind="disabled"></payment-terms-field>
          <small if.bind="hint" class="muted" t.bind="hint"></small>
        </div>
        <p class="ds-caption">Stored as <code>\${value || '(empty)'}</code></p>
      </div>
    `,
    components: [PaymentTermsField],
  }),
  args: { value: 'NET_30', disabled: false, hint: '' },
  argTypes: {
    value: { control: 'text', description: 'Payment term code: NET_n, EOM_n or EONM_n (n from 0 to 365), or a legacy code.' },
    disabled: { control: 'boolean' },
    hint: { control: 'select', options: ['', 'paymentTerms.partner-hint'] },
  },
  parameters: {
    docs: {
      description: {
        component:
          'Sets a payment term as a number of days counted from the invoice date, the end of the month or the end of next month. ' +
          'Used for the company’s own default in Settings and per partner, where an empty value means the partner follows the company default. ' +
          'Changing the basis on an empty field fills in 0 days so “end of month” works in one step. Days are capped at 365; the field stores a code, shown under each example.',
      },
    },
  },
};

export default meta;

export const InvoiceDate: Story = {
  name: '30 days after invoice date',
};

export const EndOfMonth: Story = {
  name: 'End of month',
  args: { value: 'EOM_0' },
};

export const EndOfNextMonth: Story = {
  name: '15 days after end of next month',
  args: { value: 'EONM_15' },
};

export const PartnerDefault: Story = {
  name: 'Empty on a partner',
  args: { value: '', hint: 'paymentTerms.partner-hint' },
};

export const Legacy: Story = {
  name: 'Legacy code',
  parameters: { docs: { description: { story: 'Old codes such as `60_DAYS` are read and shown in the new form; the next edit stores the new code.' } } },
  args: { value: '60_DAYS' },
};

export const Disabled: Story = {
  name: 'Read-only',
  args: { value: 'NET_14', disabled: true },
};

export const Phone: Story = {
  name: 'Phone',
  globals: { viewport: { value: 'phone', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 220 } } },
  args: { value: 'EONM_15' },
};

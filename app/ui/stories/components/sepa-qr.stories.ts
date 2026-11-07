import type { Meta, Story } from '../support/story';
import { SepaQr } from '../../src/components/sepa-qr/sepa-qr';
import { bakery } from '../support/mocks/fixtures';

const meta: Meta = {
  title: 'Components/SEPA QR code',
  render: () => ({
    template: `
      <div class="ds-frame sheet" style="display: inline-block">
        <sepa-qr
          beneficiary-name.bind="beneficiaryName"
          beneficiary-bic.bind="beneficiaryBic"
          beneficiary-account-number.bind="beneficiaryAccountNumber"
          amount-euro.bind="amountEuro"
          creditor-reference.bind="creditorReference"
          remittance-information.bind="remittanceInformation"
          size.bind="size">
        </sepa-qr>
      </div>
    `,
    components: [SepaQr],
  }),
  args: {
    beneficiaryName: bakery.paymentAccountName,
    beneficiaryBic: bakery.bic,
    beneficiaryAccountNumber: bakery.iban,
    amountEuro: 145.2,
    creditorReference: '090933755493',
    remittanceInformation: '',
    size: 256,
  },
  parameters: {
    docs: {
      description: {
        component:
          'An EPC (SEPA credit transfer) QR code that a banking app can scan to fill in a payment. It is shown in the “Pay this invoice” dialog of an incoming invoice. ' +
          'A Belgian structured communication goes in as creditor reference; otherwise the invoice number is sent as free text. ' +
          'The code draws nothing until both a name and an account number are present, and draws nothing either if the library rejects the data. ' +
          'The library paints its own white square with a rounded sepaQR.eu frame, so the code stays scannable in dark mode.',
      },
    },
  },
};

export default meta;

export const StructuredReference: Story = {
  name: 'Invoice with structured communication',
};

export const FreeText: Story = {
  name: 'Free-text reference',
  args: { creditorReference: '', remittanceInformation: 'INV-2026-0141' },
};

export const OpenAmount: Story = {
  name: 'Without amount',
  parameters: { docs: { description: { story: 'The payer types the amount in their banking app.' } } },
  args: { amountEuro: '' },
};

export const Small: Story = {
  name: 'Small, 160 px',
  args: { size: 160 },
};

export const Incomplete: Story = {
  name: 'Missing account number',
  parameters: { docs: { description: { story: 'Renders nothing: the host should hide the QR action when there is no IBAN.' } } },
  args: { beneficiaryAccountNumber: '' },
};

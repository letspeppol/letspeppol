import { customElement } from 'aurelia';
import { expect, userEvent, within } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import '../../src/components/donations/donations.css';
import { SponsorPaymentModal } from '../../src/components/donations/sponsor-payment-modal';
import { Alert } from '../../src/components/alert/alert';
import { sessionMocks } from '../support/mocks/session';
import { donationMocks, type DonationOptions } from '../support/mocks/components';
import { bakery } from '../support/mocks/fixtures';
import type { CompanyDto } from '../../src/services/app/company-service';

@customElement({
  name: 'sponsor-modal-opener',
  template: `
    <button type="button" class="btn" click.trigger="open()">Open dialog</button>
    <sponsor-payment-modal component.ref="modal"></sponsor-payment-modal>
  `,
  dependencies: [SponsorPaymentModal],
})
class SponsorModalOpener {
  modal?: SponsorPaymentModal;

  attached() {
    void this.open();
  }

  async open() {
    await this.modal?.showModal();
  }
}

const dutchBakery: CompanyDto = {
  ...bakery,
  name: 'Bakkerij Jansen B.V.',
  displayName: 'Bakkerij Jansen',
  vatNumber: 'NL812345678B01',
  registeredOffice: { street: 'Breestraat', houseNumber: '40', postalCode: '2311 CS', city: 'Leiden', countryCode: 'NL' },
};

const dialog = (donations: DonationOptions = {}, company: CompanyDto = bakery) => () => ({
  template: '<sponsor-modal-opener></sponsor-modal-opener><alert></alert>',
  components: [SponsorModalOpener, Alert],
  items: [...sessionMocks({ company }), ...donationMocks(donations)],
});

const confirm = async ({ canvasElement }: { canvasElement: HTMLElement }) => {
  await within(canvasElement).findByRole('spinbutton');
  const action = canvasElement.querySelector<HTMLButtonElement>('.sponsor-payment-modal .modal-footer .btn.primary');
  if (action) {
    await userEvent.click(action);
  }
};

const meta: Meta = {
  title: 'Components/Sponsor payment modal',
  parameters: {
    docs: {
      story: { inline: false, iframeHeight: 760 },
      description: {
        component:
          'Opened from Donate in the donation dock (admins only). A contribution is not a card payment: BARGE vzw sends the company a Peppol invoice, so the dialog explains that first and shows the VAT consequences before anything is created. ' +
          'Preset amounts show their total including VAT; another amount can be typed. Belgian companies pay 21% VAT, other EU companies get reverse charge. ' +
          'Above 2,500 EUR the action becomes a request for a sponsor package instead of an invoice. The name on the sponsor page defaults to the company name. ' +
          'Its styles live in `donations.css`, so it only looks right next to the dock that opens it.',
      },
    },
  },
};

export default meta;

export const Belgian: Story = {
  name: 'Belgian company, 21% VAT',
  render: dialog(),
};

export const ReverseCharge: Story = {
  name: 'EU company, reverse charge',
  render: dialog({}, dutchBakery),
};

export const SponsorPackage: Story = {
  name: 'Above the self-service limit',
  render: dialog(),
  play: async ({ canvasElement }) => {
    const input = await within(canvasElement).findByRole('spinbutton');
    await userEvent.clear(input);
    await userEvent.type(input, '3000');
    await userEvent.tab();
  },
};

export const Sending: Story = {
  name: 'Creating the invoice',
  render: dialog({ sponsorOutcome: 'pending' }),
  play: confirm,
};

export const InvoiceCreated: Story = {
  name: 'Invoice created',
  render: dialog({ sponsorOutcome: 'invoice' }),
  play: async (context) => {
    await confirm(context);
    await expect(await within(context.canvasElement).findByRole('heading', { level: 3 })).toBeVisible();
  },
};

export const Failed: Story = {
  name: 'Invoice not created',
  render: dialog({ sponsorOutcome: 'failed' }),
  play: confirm,
};

export const Phone: Story = {
  name: 'Phone bottom sheet',
  globals: { viewport: { value: 'phone', isRotated: false } },
  render: dialog(),
};

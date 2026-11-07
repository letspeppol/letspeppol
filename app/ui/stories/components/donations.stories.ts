import type { Meta, Story } from '../support/story';
import { Donations } from '../../src/components/donations/donations';
import { sessionMocks, type SessionOptions } from '../support/mocks/session';
import { donationMocks, openCollectiveStats, type DonationOptions } from '../support/mocks/components';
import { atelier } from '../support/mocks/fixtures';

const dock = (donations: DonationOptions = {}, session: SessionOptions = {}) => () => ({
  template: '<donations class="donation-dock"></donations>',
  components: [Donations],
  items: [...sessionMocks(session), ...donationMocks(donations)],
});

const meta: Meta = {
  title: 'Components/Donation dock',
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 180 },
      description: {
        component:
          'Let’s Peppol is free and paid for by donations and sponsors, so every app screen ends with this quiet dock: how many companies and invoices the network carries, the current sponsor’s logo, the two most recent contributions and a way to donate or follow. ' +
          'On desktop it is pinned to the bottom of the window as a full-width strip; on tablets and phones it sits at the end of the page content instead, with the secondary line and recent contributions dropped. ' +
          'Only company admins see Donate, because a donation creates an invoice for the company. ' +
          'Sponsor logos rotate every minute; when no sponsor contributions exist, recent OpenCollective donations are shown instead.',
      },
    },
  },
};

export default meta;

export const Admin: Story = {
  name: 'Admin',
  render: dock(),
};

export const User: Story = {
  name: 'User without Donate',
  render: dock({}, { company: atelier, admin: false }),
};

export const OpenCollective: Story = {
  name: 'OpenCollective donations only',
  render: dock({ stats: openCollectiveStats }),
};

export const WithoutSponsor: Story = {
  name: 'Without sponsor logo',
  render: dock({ sponsors: { sponsors: [] } }),
};

export const Tablet: Story = {
  name: 'Tablet portrait',
  globals: { viewport: { value: 'tabletPortrait', isRotated: false } },
  render: dock(),
};

export const Phone: Story = {
  name: 'Phone',
  globals: { viewport: { value: 'phone', isRotated: false } },
  parameters: { docs: { story: { inline: false, iframeHeight: 260 } } },
  render: dock(),
};

import type { Meta, Story } from '../support/story';
import { Sponsors } from '../../src/sponsor/sponsors';
import type { SponsorContributionDto } from '../../src/services/app/statistics-service';
import { sessionMocks } from '../support/mocks/session';
import { shellServices } from '../support/mocks/business';
import { pending, rejected, resolved } from '../support/mocks/stub';

function screen(contributions?: () => Promise<SponsorContributionDto[]>) {
  return () => ({ items: [...sessionMocks(), ...shellServices({ contributions })] });
}

const meta: Meta = {
  title: 'Screens/Sponsors',
  component: Sponsors,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 760 },
      description: {
        component:
          'The sponsors page thanks the companies and people who pay for keeping Let’s Peppol free, newest first. ' +
          'It is a plain ledger: date, name, their message and the amount, right-aligned in the currency they paid in. ' +
          'Loading, failure and an empty list each get one muted line inside the sheet. ' +
          'It is reached from the donation bar at the bottom of every screen; on phones each contribution becomes a card titled with the sponsor’s name.',
      },
    },
  },
};

export default meta;

export const Default: Story = {
  name: 'Contributions',
  parameters: { docs: { description: { story: 'Includes an anonymous gift without a message, a long name with a long message and a payment in another currency.' } } },
  render: screen(),
};

export const Loading: Story = {
  name: 'Loading',
  render: screen(pending()),
};

export const LoadFailed: Story = {
  name: 'Could not load',
  render: screen(rejected(503)),
};

export const Empty: Story = {
  name: 'No sponsors yet',
  render: screen(resolved([])),
};

export const Phone: Story = {
  name: 'Phone, stacked cards',
  globals: { viewport: { value: 'phone', isRotated: false } },
  render: screen(),
};

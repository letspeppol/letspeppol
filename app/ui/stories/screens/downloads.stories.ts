import type { Meta, Story } from '../support/story';
import { Downloads } from '../../src/download/downloads';
import type { DownloadJobDto } from '../../src/services/app/download-job-service';
import { sessionMocks } from '../support/mocks/session';
import { downloadJobs, downloadServices, findElement, fullDownloadJobs, shellServices } from '../support/mocks/business';
import { pending, rejected, resolved } from '../support/mocks/stub';

function screen(jobs: () => Promise<DownloadJobDto[]> = resolved(downloadJobs)) {
  return () => ({ items: [...sessionMocks(), ...shellServices(), ...downloadServices(jobs)] });
}

const meta: Meta = {
  title: 'Screens/Downloads',
  component: Downloads,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 900 },
      description: {
        component:
          'Downloads turns a period of finalised invoices and credit notes into a ZIP of UBL files for the bookkeeper. ' +
          'The form on top creates an archive (both dates in the same year, at most five active archives); the table below lists every archive with a status pill: queued and generating in the warning tone, ready in green, failed in red with a Try again button. ' +
          'Only a ready archive has a Download button. The table polls while something is queued or generating, and on phones each archive becomes a card with its period as the title.',
      },
    },
  },
};

export default meta;

export const AllStatuses: Story = {
  name: 'Archives in every status',
  parameters: { docs: { description: { story: 'Queued, generating, ready and failed archives side by side.' } } },
  render: screen(),
};

export const Empty: Story = {
  name: 'No archives yet',
  render: screen(resolved([])),
};

export const Loading: Story = {
  name: 'Loading',
  render: screen(pending()),
};

export const LoadFailed: Story = {
  name: 'Archives could not be loaded',
  parameters: { docs: { description: { story: 'The failure is announced with a toast from the app shell, so the page itself falls back to the empty state.' } } },
  render: screen(rejected(503)),
};

export const LimitReached: Story = {
  name: 'Five active archives',
  parameters: { docs: { description: { story: 'With five archives queued, generating or ready, the counter turns to its full state and Create archive is disabled with an explanation.' } } },
  render: screen(resolved(fullDownloadJobs)),
};

export const InvalidPeriod: Story = {
  name: 'Period across two years',
  parameters: { docs: { description: { story: 'Both dates must fall in the same year; the error appears under the form as soon as they do not.' } } },
  render: screen(),
  play: async ({ canvasElement }) => {
    const from = await findElement<HTMLInputElement>(canvasElement, '.download-form input[type="date"]');
    from.value = '2025-12-01';
    from.dispatchEvent(new Event('input', { bubbles: true }));
    from.dispatchEvent(new Event('change', { bubbles: true }));
  },
};

export const Tablet: Story = {
  name: 'Tablet portrait',
  globals: { viewport: { value: 'tabletPortrait', isRotated: false } },
  render: screen(),
};

export const Phone: Story = {
  name: 'Phone, stacked cards',
  globals: { viewport: { value: 'phone', isRotated: false } },
  render: screen(),
};

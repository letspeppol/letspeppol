import { expect, userEvent, within } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { PeppolSearch } from '../../src/components/search/peppol-search';
import { Overlay } from '../../src/components/overlay/overlay';
import { Alert } from '../../src/components/alert/alert';
import { peppolDirectoryMocks, type Outcome } from '../support/mocks/components';
import { partners } from '../support/mocks/fixtures';

const anker = partners[0];

const searchWith = (outcome: Outcome, filled = true) => () => ({
  template: `
    <div class="ds-frame sheet" style="max-width: 36rem; min-height: 22rem">
      <peppol-search name.bind="name" vat-number.bind="vatNumber" peppol-id.two-way="peppolId" select-match-function.bind="select"></peppol-search>
    </div>
    <overlay></overlay>
    <alert></alert>
  `,
  components: [PeppolSearch, Overlay, Alert],
  items: peppolDirectoryMocks(outcome),
  props: {
    name: filled ? 'Brasserie Het Anker' : '',
    vatNumber: filled ? anker.vatNumber : '',
    peppolId: '',
    select: (name: string, participantId: string) => console.info('[storybook] match selected', name, participantId),
  },
});

const searchByName = async ({ canvasElement }: { canvasElement: HTMLElement }) => {
  await userEvent.click(within(canvasElement).getByRole('button', { name: 'Search By Company Name' }));
};

const meta: Meta = {
  title: 'Components/Peppol directory search',
  parameters: {
    docs: {
      story: { inline: false, iframeHeight: 460 },
      description: {
        component:
          'Finds a Peppol participant ID in the public Peppol directory, by company name or by VAT number, and fills it in. ' +
          'While the directory answers, the full-screen overlay blocks the page; an empty answer raises a warning toast and a failure a danger toast. ' +
          'No screen uses this component today (the invoice customer dialog has its own search), and it shows: the button labels and the “Peppol Id:” prefix are hard-coded English and Title Case, and the result list relies on `.autocomplete-list` styles that only load with the invoice editor, so here it renders as a bare list.',
      },
    },
  },
};

export default meta;

export const NothingToSearch: Story = {
  name: 'Nothing to search with',
  render: searchWith('results', false),
};

export const Ready: Story = {
  name: 'Name and VAT number known',
  render: searchWith('results'),
};

export const Searching: Story = {
  name: 'Searching',
  render: searchWith('pending'),
  play: searchByName,
};

export const Results: Story = {
  render: searchWith('results'),
  play: async (context) => {
    await searchByName(context);
    await expect(await within(context.canvasElement).findByRole('listbox')).toBeInTheDocument();
  },
};

export const NoResults: Story = {
  name: 'No results',
  render: searchWith('empty'),
  play: searchByName,
};

export const Failed: Story = {
  name: 'Directory unavailable',
  render: searchWith('failed'),
  play: searchByName,
};

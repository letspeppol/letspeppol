import { expect, userEvent, within } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { CompanySearch } from '../../src/components/search/company-search';
import { companySearchMocks, type Outcome } from '../support/mocks/components';
import type { KycCompanyResponse } from '../../src/services/kyc/registration-service';

const searchWith = (outcome: Outcome, query = '') => () => ({
  template: `
    <div class="ds-frame sheet" style="max-width: 36rem; min-height: 22rem">
      <company-search search-query.two-way="query" select-company-function.bind="select"></company-search>
    </div>
  `,
  components: [CompanySearch],
  items: companySearchMocks(outcome),
  props: {
    query,
    select: (company: KycCompanyResponse) => console.info('[storybook] company selected', company.name),
  },
});

const typeAndSearch = async ({ canvasElement }: { canvasElement: HTMLElement }) => {
  const canvas = within(canvasElement);
  const input = canvas.getByRole('combobox');
  await userEvent.type(input, 'Bakkerij{Enter}');
};

const meta: Meta = {
  title: 'Components/Company search',
  parameters: {
    docs: {
      story: { inline: false, iframeHeight: 420 },
      description: {
        component:
          'Looks up a Belgian company in the company register (KBO) by name, so a new partner or customer can be filled in from official data. ' +
          'The search only starts on Enter or the search button, after at least five characters, because every lookup hits the register. ' +
          'Results show name, VAT number and registered address; arrow keys move the highlight and Enter picks it. ' +
          'Known gaps worth designing for: there is no loading indicator while the register answers, and when nothing matches the list simply stays closed.',
      },
    },
  },
};

export default meta;

export const Empty: Story = {
  render: searchWith('results'),
};

export const TooShort: Story = {
  name: 'Too short to search',
  render: searchWith('results', 'Bakk'),
};

export const Searching: Story = {
  name: 'Waiting for the register',
  parameters: {
    docs: { description: { story: 'The register has not answered yet; nothing on screen says so.' } },
  },
  render: searchWith('pending'),
  play: typeAndSearch,
};

export const Results: Story = {
  render: searchWith('results'),
  play: async (context) => {
    await typeAndSearch(context);
    await expect(await within(context.canvasElement).findByRole('listbox')).toBeVisible();
  },
};

export const NoResults: Story = {
  name: 'No results',
  parameters: {
    docs: { description: { story: 'The register returned nothing; the list stays closed and the person gets no message.' } },
  },
  render: searchWith('empty'),
  play: typeAndSearch,
};

export const Phone: Story = {
  name: 'Phone with results',
  globals: { viewport: { value: 'phone', isRotated: false } },
  render: searchWith('results'),
  play: async (context) => {
    await typeAndSearch(context);
    await expect(await within(context.canvasElement).findByRole('listbox')).toBeVisible();
  },
};

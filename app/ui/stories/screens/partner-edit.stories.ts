import { fireEvent } from 'storybook/test';
import type { Meta, Story } from '../support/story';
import { Partners } from '../../src/partner/partners';
import { PartnerContext } from '../../src/partner/partner-context';
import type { PartnerDto } from '../../src/services/app/partner-service';
import { CompanyPermission } from '../../src/services/app/company-permission';
import { sessionMocks, type SessionOptions } from '../support/mocks/session';
import { findElement, partnerServices, shellServices } from '../support/mocks/business';
import { atelier, partners } from '../support/mocks/fixtures';
import { provide } from '../support/mocks/stub';

function editing(partner: PartnerDto | 'new', session: SessionOptions = {}) {
  return () => {
    const context = new PartnerContext();
    if (partner === 'new') {
      context.newPartner();
    } else {
      context.selectedPartner = structuredClone(partner);
    }
    return { items: [...sessionMocks(session), ...shellServices(), ...partnerServices(), provide(PartnerContext, context)] };
  };
}

const brasserie: PartnerDto = {
  ...partners[0],
  paymentTerms: 'NET_30',
  iban: 'BE62 5100 0754 7061',
  paymentAccountName: 'Brasserie Het Anker NV',
};

const incomplete: PartnerDto = {
  name: 'Brouwerij Het Sas BV',
  identifier: '0412.536.978',
  vatNumber: 'BE0412536978',
  email: '',
  peppolId: '0412536978',
  customer: true,
  supplier: false,
  registeredOffice: { street: 'Kapelstraat 4', postalCode: '3000', city: 'Leuven', countryCode: 'BE' },
};

const meta: Meta = {
  title: 'Screens/Partners/Edit',
  component: Partners,
  parameters: {
    layout: 'fullscreen',
    docs: {
      story: { inline: false, iframeHeight: 1100 },
      description: {
        component:
          'One form for adding and editing a customer or supplier, split into four flat sheets: company, Peppol, address and payment details. ' +
          'Typing a company name, enterprise number, VAT number or Belgian Peppol ID looks the company up in the register and fills the empty fields; it never overwrites what the person typed. ' +
          'Only the name and a Peppol ID with a scheme (0208:…) are required, and the save button stays disabled until both are there. ' +
          'Under 1024px the cancel and save buttons sit in the sticky action bar; without the partner permission the form is read-only and the only action is Back to partners.',
      },
    },
  },
};

export default meta;

export const NewPartner: Story = {
  name: 'New partner',
  parameters: { docs: { description: { story: 'An empty form defaults to a Belgian customer; save is disabled until there is a name and a Peppol ID.' } } },
  render: editing('new'),
};

export const ExistingPartner: Story = {
  name: 'Existing partner',
  render: editing(brasserie),
};

export const RegisterLookup: Story = {
  name: 'Company register lookup',
  parameters: { docs: { description: { story: 'After five characters and Enter (or the search button) the register results open under the name field.' } } },
  render: editing('new'),
  play: async ({ canvasElement }) => {
    const name = await findElement(canvasElement, '#companyNameSearch');
    fireEvent.input(name, { target: { value: 'Brouwerij' } });
    fireEvent.keyDown(name, { key: 'Enter' });
    await findElement(canvasElement, '.company-search-item');
  },
};

export const IncompletePeppolId: Story = {
  name: 'Peppol ID without scheme',
  parameters: { docs: { description: { story: 'The Peppol ID lacks the 0208: scheme, so Save partner stays disabled; the hint below the field is the only explanation.' } } },
  render: editing(incomplete),
};

export const ReadOnly: Story = {
  name: 'Read-only user',
  parameters: { docs: { description: { story: 'Without the partner permission every field is disabled and the only action is going back.' } } },
  render: editing(brasserie, {
    company: atelier,
    admin: false,
    permissionMask: CompanyPermission.INVOICE_READ | CompanyPermission.INVOICE_DRAFT,
  }),
};

export const Tablet: Story = {
  name: 'Tablet portrait',
  globals: { viewport: { value: 'tabletPortrait', isRotated: false } },
  render: editing(brasserie),
};

export const Phone: Story = {
  name: 'Phone',
  globals: { viewport: { value: 'phone', isRotated: false } },
  render: editing(brasserie),
};

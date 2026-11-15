import type { CompanyDto } from '../../../src/services/app/company-service';
import type { OwnershipSummary } from '../../../src/services/app/ownership-service';
import type { PartnerDto } from '../../../src/services/app/partner-service';
import type { ProductDto } from '../../../src/services/app/product-service';

export const BAKERY_PEPPOL_ID = '0208:0705969661';
export const ATELIER_PEPPOL_ID = '0208:0123456789';

export const bakery: CompanyDto = {
  peppolId: BAKERY_PEPPOL_ID,
  identifier: '0705969661',
  vatNumber: 'BE0705969661',
  name: 'Bakkerij De Smet BV',
  displayName: 'Bakkerij De Smet',
  subscriber: 'Lotte De Smet',
  subscriberEmail: 'lotte@bakkerijdesmet.be',
  paymentTerms: 'NET_30',
  iban: 'BE68 5390 0754 7034',
  bic: 'TRIOBEBB',
  paymentAccountName: 'Bakkerij De Smet BV',
  vatRuleset: 'VAT_REGISTERED',
  lastInvoiceReference: 'INV-2026-0141',
  lastCreditNoteReference: 'CN-2026-0007',
  peppolActive: true,
  enableEmailNotification: true,
  addAttachmentToNotification: false,
  addPdfToSendingInvoice: true,
  emailNotificationCcListIncoming: 'boekhouding@bakkerijdesmet.be',
  emailNotificationCcListOutgoing: '',
  companyGroup: 'USER',
  registeredOffice: {
    street: 'Kerkstraat',
    houseNumber: '12',
    postalCode: '9000',
    city: 'Gent',
    countryCode: 'BE',
  },
};

export const atelier: CompanyDto = {
  ...bakery,
  peppolId: ATELIER_PEPPOL_ID,
  identifier: '0123456789',
  vatNumber: 'BE0123456789',
  name: 'Atelier Dupont SRL',
  displayName: 'Atelier Dupont',
  subscriber: 'Camille Dupont',
  subscriberEmail: 'camille@atelierdupont.be',
  iban: 'BE71 0961 2345 6769',
  bic: 'GKCCBEBB',
  paymentAccountName: 'Atelier Dupont SRL',
  lastInvoiceReference: 'F2026/088',
  registeredOffice: {
    street: 'Rue de la Station',
    houseNumber: '4',
    postalCode: '5000',
    city: 'Namur',
    countryCode: 'BE',
  },
};

export const ownerships: OwnershipSummary[] = [
  { peppolId: BAKERY_PEPPOL_ID, companyName: bakery.name, type: 'ADMIN', peppolActive: true },
  { peppolId: ATELIER_PEPPOL_ID, companyName: atelier.name, type: 'USER', peppolActive: true },
];

export const currentAccount = {
  id: 'acc-1',
  name: 'Lotte De Smet',
  email: 'lotte@bakkerijdesmet.be',
};

export const partners: PartnerDto[] = [
  {
    id: 1,
    name: 'Brasserie Het Anker NV',
    identifier: '0401234567',
    vatNumber: 'BE0401234567',
    email: 'facturen@hetanker.be',
    peppolId: '0208:0401234567',
    customer: true,
    supplier: false,
    paymentTerms: 'NET_30',
    registeredOffice: { street: 'Guido Gezellelaan', houseNumber: '49', postalCode: '2800', city: 'Mechelen', countryCode: 'BE' },
  },
  {
    id: 2,
    name: 'Hotel Le Mosan SA',
    identifier: '0459876543',
    vatNumber: 'BE0459876543',
    email: 'comptabilite@lemosan.be',
    peppolId: '0208:0459876543',
    customer: true,
    supplier: false,
    paymentTerms: 'NET_14',
    registeredOffice: { street: 'Quai de Meuse', houseNumber: '7', postalCode: '5000', city: 'Namur', countryCode: 'BE' },
  },
  {
    id: 3,
    name: 'Molens Vandewiele BV',
    identifier: '0423456789',
    vatNumber: 'BE0423456789',
    email: 'sales@vandewiele-molens.be',
    peppolId: '0208:0423456789',
    customer: false,
    supplier: true,
    iban: 'BE43 0682 4441 7101',
    paymentAccountName: 'Molens Vandewiele BV',
    registeredOffice: { street: 'Molenstraat', houseNumber: '3', postalCode: '8800', city: 'Roeselare', countryCode: 'BE' },
  },
  {
    id: 4,
    name: 'Koffiebranderij Mokabon – Groothandel in koffie, thee en aanverwante producten',
    identifier: '0412345678',
    vatNumber: 'BE0412345678',
    email: 'info@mokabon.be',
    peppolId: '0208:0412345678',
    customer: true,
    supplier: true,
    registeredOffice: { street: 'Vlaamsesteenweg', houseNumber: '128', postalCode: '1000', city: 'Brussel', countryCode: 'BE' },
  },
  {
    id: 5,
    name: 'Café De Zwaan',
    identifier: '0798123456',
    peppolId: '0208:0798123456',
    customer: true,
    supplier: false,
  },
];

export const products: ProductDto[] = [
  { id: 1, name: 'Desembrood volkoren', reference: 'BR-001', barcode: '5410000000011', costPrice: 1.1, salePrice: 3.6, taxPercentage: 6, stockQuantity: 40, categoryId: 1 },
  { id: 2, name: 'Croissant roomboter', reference: 'PA-014', costPrice: 0.35, salePrice: 1.45, taxPercentage: 6, stockQuantity: 120, categoryId: 2 },
  { id: 3, name: 'Taart van de week (12 personen)', description: 'Wisselend seizoensfruit op frangipane', reference: 'PA-090', costPrice: 9.8, salePrice: 32, taxPercentage: 6, categoryId: 2 },
  { id: 4, name: 'Broodjeslunch per persoon', description: 'Drie belegde broodjes, soep en fruit', reference: 'CA-200', salePrice: 14.5, taxPercentage: 12, categoryId: 4 },
  { id: 5, name: 'Levering en opstelling', reference: 'SRV-01', salePrice: 45, taxPercentage: 21 },
];

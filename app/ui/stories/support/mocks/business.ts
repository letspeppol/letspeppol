import type { IRegistration } from '@aurelia/kernel';
import { StatisticsService, type DonationStatsDto, type SponsorContributionDto, type Totals } from '../../../src/services/app/statistics-service';
import { SponsorService, type SponsorsDto } from '../../../src/services/app/sponsor-service';
import { PartnerService, type PartnerDto } from '../../../src/services/app/partner-service';
import { ProductService, type ProductDto } from '../../../src/services/app/product-service';
import { ProductCategoryService, type ProductCategoryDto } from '../../../src/services/app/product-category-service';
import { DownloadJobService, type DownloadJobDto } from '../../../src/services/app/download-job-service';
import { CompanySearchService } from '../../../src/services/kyc/company-search-service';
import type { KycCompanyResponse } from '../../../src/services/kyc/registration-service';
import { partners, productCategories, products } from './fixtures';
import { waitFor } from 'storybook/test';
import { provide, resolved, stub } from './stub';

type Async<T> = () => Promise<T>;

export const typicalTotals: Totals = {
  exclVat: {
    receivableThisYear: 48210.5,
    receivableOpen: 6420.75,
    receivableOverdue: 1180,
    payableThisYear: 21876.4,
    payableOpen: 2310.15,
    payableOverdue: 0,
  },
  inclVat: {
    receivableThisYear: 52384.27,
    receivableOpen: 7105.62,
    receivableOverdue: 1250.8,
    payableThisYear: 25390.12,
    payableOpen: 2795.28,
    payableOverdue: 0,
  },
  erroredUnseenCount: 0,
};

export const totalsWithProblems: Totals = {
  exclVat: { ...typicalTotals.exclVat, payableOverdue: 845.6 },
  inclVat: { ...typicalTotals.inclVat, payableOverdue: 1023.18 },
  erroredUnseenCount: 2,
};

export const emptyTotals: Totals = {
  exclVat: { receivableThisYear: 0, receivableOpen: 0, receivableOverdue: 0, payableThisYear: 0, payableOpen: 0, payableOverdue: 0 },
  inclVat: { receivableThisYear: 0, receivableOpen: 0, receivableOverdue: 0, payableThisYear: 0, payableOpen: 0, payableOverdue: 0 },
  erroredUnseenCount: 0,
};

export const sponsorContributions: SponsorContributionDto[] = [
  { name: 'Boekhoudkantoor Verbeke', message: 'Dank voor het gratis alternatief. Onze klanten zijn er blij mee!', amount: 250, currency: 'EUR', date: '2026-10-28T10:15:00Z' },
  { name: 'Bakkerij De Smet', message: 'Peppol zonder abonnement, zo hoort het.', amount: 25, currency: 'EUR', date: '2026-10-21T08:02:00Z' },
  { name: 'Incognito', message: '', amount: 10, currency: 'EUR', date: '2026-10-14T19:44:00Z' },
  { name: 'Fiduciaire Lambert & Associés SRL – cabinet comptable et conseil fiscal pour indépendants', message: 'Merci pour cet outil libre. Nous le recommandons à tous nos clients qui débutent avec la facturation électronique obligatoire en 2026.', amount: 500, currency: 'EUR', date: '2026-09-30T12:00:00Z' },
  { name: 'Jonas Peeters', message: 'Keep it up', amount: 15, currency: 'EUR', date: '2026-09-02T16:20:00Z' },
  { name: 'OpenCollective supporter', message: 'OpenCollective contribution', amount: 100, currency: 'USD', date: '2026-08-11T07:31:00Z' },
];

function sponsorLogo(label: string, color: string): string {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="160" height="48" viewBox="0 0 160 48"><rect width="160" height="48" rx="6" fill="${color}"/><text x="80" y="30" font-family="system-ui, sans-serif" font-size="16" font-weight="700" fill="#fff" text-anchor="middle">${label}</text></svg>`;
  return `data:image/svg+xml,${encodeURIComponent(svg)}`;
}

export const sponsors: SponsorsDto = {
  sponsors: [
    { name: 'Verbeke Accountancy', logoUrl: sponsorLogo('Verbeke', '#1d6f5f'), url: 'https://example.org/verbeke' },
    { name: 'Lambert & Associés', logoUrl: sponsorLogo('Lambert &amp; Co', '#7a3b8f'), url: 'https://example.org/lambert' },
  ],
};

export const donationStats: DonationStatsDto = {
  totalContributions: 37,
  totalProcessed: 18452,
  processedToday: 214,
  maxProcessedLastWeek: 1310,
  invoicesRemaining: 4200,
  activeCompanies: 1268,
  transactions: [],
  contributions: sponsorContributions,
};

export const kycCompanies: KycCompanyResponse[] = [
  { id: 11, peppolId: '0208:0477425963', identifier: '0477425963', vatNumber: 'BE0477425963', name: 'Brouwerij Van Steenberge NV', street: 'Lindenlaan 1', city: 'Ertvelde', postalCode: '9940' },
  { id: 12, peppolId: '0208:0412536978', identifier: '0412536978', vatNumber: 'BE0412536978', name: 'Brouwerij Het Sas BV', street: 'Kapelstraat 4', city: 'Leuven', postalCode: '3000' },
];

const extraPartners: Array<[string, string, string, string, boolean, boolean]> = [
  ['Slagerij Vermeulen', 'slagerijvermeulen.be', '2000', 'Antwerpen', true, false],
  ['Hotel Ter Duinen NV', 'terduinen.be', '8670', 'Koksijde', true, false],
  ['Zuivelhoeve De Kempen', 'zuivelhoeve.be', '2440', 'Geel', false, true],
  ['Restaurant La Table de Marie', 'latabledemarie.be', '4000', 'Liège', true, false],
  ['Verpakkingen Claes BV', 'claes-verpakkingen.be', '3500', 'Hasselt', false, true],
  ['Tearoom Den Appel', 'denappel.be', '9300', 'Aalst', true, false],
  ['Fruithandel Goossens', 'goossensfruit.be', '3800', 'Sint-Truiden', false, true],
  ['Brasserie du Parc SRL', 'brasserieduparc.be', '1050', 'Ixelles', true, false],
  ['Energie Wallonie SA', 'energie-wallonie.be', '5100', 'Jambes', false, true],
  ['Kinderdagverblijf Het Nestje vzw', 'hetnestje.be', '9050', 'Gentbrugge', true, false],
  ['Eierboerderij Van Damme', 'vandamme-eieren.be', '8820', 'Torhout', false, true],
  ['Catering Delvaux SRL', 'delvaux-catering.be', '1400', 'Nivelles', true, true],
  ['Bistro De Gouden Leeuw', 'goudenleeuw.be', '8000', 'Brugge', true, false],
  ['Schoonmaakbedrijf Proper BV', 'proper.be', '9000', 'Gent', false, true],
  ['Golfclub Oudenaarde', 'golfoudenaarde.be', '9700', 'Oudenaarde', true, false],
  ['Melkerij Ardennes SA', 'melkerie-ardennes.be', '6600', 'Bastogne', false, true],
  ['Sporthal De Brug', 'sporthaldebrug.be', '2800', 'Mechelen', true, false],
  ['Feestzaal Rozenhof', 'rozenhof.be', '9200', 'Dendermonde', true, false],
  ['Suikerraffinaderij Tienen NV', 'tiense-suiker.be', '3300', 'Tienen', false, true],
  ['Gemeentelijke Basisschool Ter Beke', 'terbeke.be', '9080', 'Lochristi', true, false],
  ['Koffiehuis Zoet & Zout', 'zoetenzout.be', '3000', 'Leuven', true, false],
];

export const manyPartners: PartnerDto[] = [
  ...partners,
  ...extraPartners.map(([name, domain, postalCode, city, customer, supplier], index): PartnerDto => {
    const identifier = String(800100200 + index * 7919).padStart(10, '0');
    return {
      id: 200 + index,
      name,
      identifier,
      vatNumber: `BE${identifier}`,
      email: `facturatie@${domain}`,
      peppolId: `0208:${identifier}`,
      customer,
      supplier,
      registeredOffice: { street: 'Stationsstraat', houseNumber: String(10 + index), postalCode, city, countryCode: 'BE' },
    };
  }),
];

export const shopCategories: ProductCategoryDto[] = productCategories.map(({ id, name, color }) => ({ id, name, color }));

export const shopProducts: ProductDto[] = [
  ...products.map((product) => (product.categoryId === 4 ? { ...product, categoryId: 3 } : product)),
  { id: 6, name: 'Rozijnenbrood', reference: 'BR-007', barcode: '5410000000073', costPrice: 1.35, salePrice: 4.2, taxPercentage: 6, stockQuantity: 18, categoryId: 1 },
  { id: 7, name: 'Koffiekoeken assortiment (doos van 12)', reference: 'PA-021', costPrice: 4.1, salePrice: 15.6, taxPercentage: 6, stockQuantity: 9, categoryId: 2 },
  { id: 8, name: 'Verjaardagstaart op maat met foto en naam, glutenvrij op aanvraag', description: 'Minstens drie werkdagen vooraf bestellen', reference: 'PA-120', costPrice: 14, salePrice: 48.5, taxPercentage: 6, categoryId: 2 },
  { id: 9, name: 'Receptie hapjes per persoon', reference: 'CA-310', salePrice: 9.75, taxPercentage: 12, categoryId: 3 },
];

const today = '2026-11-05';

export const downloadJobs: DownloadJobDto[] = [
  { id: 41, fromDate: '2026-01-01', toDate: today, status: 'PENDING', sizeBytes: null, createdOn: '2026-11-05T09:41:12', downloadCount: 0 },
  { id: 40, fromDate: '2026-10-01', toDate: '2026-10-31', status: 'PROCESSING', sizeBytes: null, createdOn: '2026-11-05T09:38:05', downloadCount: 0 },
  { id: 38, fromDate: '2026-07-01', toDate: '2026-09-30', status: 'READY', filename: '20260701-20260930.zip', sizeBytes: 18_420_113, createdOn: '2026-11-03T14:02:40', completedOn: '2026-11-03T14:03:22', expiresOn: '2026-11-10T14:03:22', downloadCount: 1 },
  { id: 36, fromDate: '2026-04-01', toDate: '2026-06-30', status: 'FAILED', sizeBytes: null, createdOn: '2026-11-02T11:20:00', completedOn: '2026-11-02T11:20:41', downloadCount: 0, failureMessage: 'The generated archive file is missing' },
  { id: 33, fromDate: '2026-01-01', toDate: '2026-03-31', status: 'READY', filename: '20260101-20260331.zip', sizeBytes: 9_884_502, createdOn: '2026-10-30T08:15:09', completedOn: '2026-10-30T08:15:51', expiresOn: '2026-11-06T08:15:51', downloadCount: 2 },
];

export const fullDownloadJobs: DownloadJobDto[] = [
  ...downloadJobs.filter((job) => job.status !== 'FAILED'),
  { id: 30, fromDate: '2026-01-01', toDate: '2026-01-31', status: 'READY', filename: '20260101-20260131.zip', sizeBytes: 2_310_448, createdOn: '2026-10-29T17:48:30', completedOn: '2026-10-29T17:48:44', expiresOn: '2026-11-05T17:48:44', downloadCount: 0 },
];

export interface ShellOptions {
  totals?: Async<Totals>;
  contributions?: Async<SponsorContributionDto[]>;
}

export function shellServices(options: ShellOptions = {}): IRegistration[] {
  const statisticsService = stub<StatisticsService>('StatisticsService', {
    getTotals: options.totals ?? resolved(typicalTotals),
    getDonationStats: resolved(donationStats),
    clearCache: () => undefined,
    clearDonationStatsCache: () => undefined,
  });
  const sponsorService = stub<SponsorService>('SponsorService', {
    getSponsors: resolved(sponsors),
    getSponsorContributions: options.contributions ?? resolved(sponsorContributions),
    sponsor: async () => ({ status: 'OK', message: '' }),
    clearCache: () => undefined,
    clearContributionsCache: () => undefined,
  });
  return [provide(StatisticsService, statisticsService), provide(SponsorService, sponsorService)];
}

export function partnerServices(list: Async<PartnerDto[]> = resolved(partners)): IRegistration[] {
  let nextId = 100;
  const partnerService = stub<PartnerService>('PartnerService', {
    getPartners: list,
    searchPartners: resolved([]),
    createPartner: async (partner: PartnerDto) => ({ ...partner, id: nextId++ }),
    updatePartner: async (_id: number, partner: PartnerDto) => partner,
    deletePartner: async () => new Response(null, { status: 204 }),
    clearCache: () => undefined,
  });
  const companySearchService = stub<CompanySearchService>('CompanySearchService', {
    searchCompany: resolved(kycCompanies, 300),
  });
  return [provide(PartnerService, partnerService), provide(CompanySearchService, companySearchService)];
}

export interface ProductServiceOptions {
  products?: Async<ProductDto[]>;
  categories?: Async<ProductCategoryDto[]>;
}

export function productServices(options: ProductServiceOptions = {}): IRegistration[] {
  let nextId = 100;
  const productService = stub<ProductService>('ProductService', {
    getProducts: options.products ?? resolved(shopProducts),
    createProduct: async (product: ProductDto) => ({ ...product, id: nextId++ }),
    updateProduct: async (_id: number, product: ProductDto) => product,
    deleteProduct: async () => new Response(null, { status: 204 }),
  });
  const productCategoryService = stub<ProductCategoryService>('ProductCategoryService', {
    getProductCategories: options.categories ?? resolved(shopCategories),
    createProductCategory: async (category: ProductCategoryDto) => ({ ...category, id: nextId++ }),
    updateProductCategory: async (_id: number, category: ProductCategoryDto) => category,
    deleteProductCategory: async () => new Response(null, { status: 204 }),
  });
  return [provide(ProductService, productService), provide(ProductCategoryService, productCategoryService)];
}

export function downloadServices(jobs: Async<DownloadJobDto[]> = resolved(downloadJobs)): IRegistration[] {
  const downloadJobService = stub<DownloadJobService>('DownloadJobService', {
    getJobs: jobs,
    createJob: async (fromDate: string, toDate: string) => ({ id: 99, fromDate, toDate, status: 'PENDING', sizeBytes: null, createdOn: new Date().toISOString(), downloadCount: 0 }),
    retryJob: async (id: number) => ({ ...downloadJobs.find((job) => job.id === id), status: 'PENDING' }),
    deleteJob: async () => undefined,
    downloadJob: async () => new Response(new Blob(['storybook'])),
  });
  return [provide(DownloadJobService, downloadJobService)];
}

export async function findElement<T extends HTMLElement = HTMLElement>(root: HTMLElement, selector: string, index = 0): Promise<T> {
  return waitFor(() => {
    const element = root.querySelectorAll<T>(selector)[index];
    if (!element) {
      throw new Error(`No element matches ${selector} [${index}]`);
    }
    return element;
  }, { timeout: 5000 });
}

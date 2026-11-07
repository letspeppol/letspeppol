import { DI, IPlatform, Registration, type IContainer } from 'aurelia';
import { StandardConfiguration } from '@aurelia/runtime-html';
import { BrowserPlatform } from '@aurelia/platform-browser';
import type { IRegistration } from '@aurelia/kernel';
import { IFeatureAck, type FeatureAckListener } from '../../../src/components/new-feature';
import { CompanySearchService } from '../../../src/services/kyc/company-search-service';
import type { KycCompanyResponse } from '../../../src/services/kyc/registration-service';
import { PeppolDirService, type PeppolDirectoryResponse } from '../../../src/services/peppol/peppol-dir-service';
import { StatisticsService, type DonationStatsDto } from '../../../src/services/app/statistics-service';
import { SponsorService, type SponsorInvoiceResponse, type SponsorsDto } from '../../../src/services/app/sponsor-service';
import {
  WelcomeNotificationService,
  type WelcomeNotificationsResponse,
} from '../../../src/services/app/welcome-notification-service';
import { BAKERY_PEPPOL_ID } from './fixtures';
import { pending, provide, rejected, resolved, stub } from './stub';

export type Outcome = 'results' | 'empty' | 'pending' | 'failed';

export function storyContainer(...registrations: IRegistration[]): IContainer {
  return DI.createContainer().register(
    Registration.instance(IPlatform, BrowserPlatform.getOrCreate(globalThis)),
    StandardConfiguration,
    ...registrations,
  );
}

export class StoryFeatureAck implements IFeatureAck {
  private readonly acknowledged: Set<string>;
  private readonly listeners = new Set<FeatureAckListener>();

  constructor(private readonly sections: Record<string, string | null>, acknowledged: string[] = []) {
    this.acknowledged = new Set(acknowledged);
  }

  isAcknowledged(id: string): boolean {
    return this.acknowledged.has(id);
  }

  acknowledge(id: string): void {
    if (this.acknowledged.has(id)) {
      return;
    }
    this.acknowledged.add(id);
    for (const listener of this.listeners) {
      listener(id);
    }
  }

  hasUnacknowledgedInSection(section: string): boolean {
    return Object.entries(this.sections).some(([id, owner]) => owner === section && !this.acknowledged.has(id));
  }

  subscribe(listener: FeatureAckListener): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }
}

export function featureAckContainer(sections: Record<string, string | null>, acknowledged: string[] = []): IContainer {
  return storyContainer(provide(IFeatureAck, new StoryFeatureAck(sections, acknowledged)));
}

export const registerCompanies: KycCompanyResponse[] = [
  {
    id: 11,
    peppolId: BAKERY_PEPPOL_ID,
    identifier: '0705969661',
    vatNumber: 'BE0705969661',
    name: 'Bakkerij De Smet BV',
    street: 'Kerkstraat',
    houseNumber: '12',
    postalCode: '9000',
    city: 'Gent',
  } as KycCompanyResponse,
  {
    id: 12,
    peppolId: '0208:0447812345',
    identifier: '0447812345',
    vatNumber: 'BE0447812345',
    name: 'Bakkerij Vermeulen & Zonen BV',
    street: 'Oudenaardsesteenweg',
    houseNumber: '231',
    postalCode: '9000',
    city: 'Gent',
  } as KycCompanyResponse,
  {
    id: 13,
    peppolId: '0208:0861234567',
    identifier: '0861234567',
    vatNumber: 'BE0861234567',
    name: 'Bakkerij-Patisserie Van den Bossche – Ambachtelijke warme bakker sinds 1952 BV',
    street: 'Grote Markt',
    houseNumber: '3',
    postalCode: '9300',
    city: 'Aalst',
  } as KycCompanyResponse,
];

export function companySearchMocks(outcome: Outcome = 'results', delayMs = 0): IRegistration[] {
  const searchCompany = {
    results: resolved(registerCompanies, delayMs),
    empty: resolved([] as KycCompanyResponse[], delayMs),
    pending: pending<KycCompanyResponse[]>(),
    failed: rejected(503),
  }[outcome];
  return [provide(CompanySearchService, stub<CompanySearchService>('CompanySearchService', { searchCompany }))];
}

export const peppolDirectory: PeppolDirectoryResponse = {
  version: '1.0',
  'total-result-count': 2,
  'used-result-count': 2,
  'result-page-index': 0,
  'result-page-count': 1,
  'first-result-index': 0,
  'last-result-index': 1,
  'query-terms': 'Brasserie',
  'creation-dt': '2026-11-05T09:12:00Z',
  matches: [
    {
      participantID: { scheme: 'iso6523-actorid-upis', value: '0208:0401234567' },
      entities: [{ name: [{ name: 'Brasserie Het Anker NV', language: 'nl' }], countryCode: 'BE' }],
    },
    {
      participantID: { scheme: 'iso6523-actorid-upis', value: '9925:be0401234567' },
      entities: [{ name: [{ name: 'Brasserie Het Anker NV', language: 'nl' }], countryCode: 'BE' }],
    },
    {
      participantID: { scheme: 'iso6523-actorid-upis', value: '0106:12345678' },
      entities: [{ name: [{ name: 'Brasserie De Anker Rotterdam B.V.', language: 'nl' }], countryCode: 'NL' }],
    },
  ],
};

export function peppolDirectoryMocks(outcome: Outcome = 'results'): IRegistration[] {
  const response = outcome === 'empty' ? { ...peppolDirectory, matches: [] } : peppolDirectory;
  const lookup = {
    results: resolved(response, 300),
    empty: resolved(response, 300),
    pending: pending<PeppolDirectoryResponse>(),
    failed: rejected(502),
  }[outcome];
  return [
    provide(PeppolDirService, stub<PeppolDirService>('PeppolDirService', { findByName: lookup, findByParticipant: lookup })),
  ];
}

const sponsorLogo = `data:image/svg+xml,${encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" width="240" height="56" viewBox="0 0 240 56">' +
    '<rect width="240" height="56" rx="6" fill="#1f6f5c"/>' +
    '<circle cx="28" cy="28" r="14" fill="none" stroke="#fff" stroke-width="4"/>' +
    '<path d="M22 28h12M28 22v12" stroke="#fff" stroke-width="4" stroke-linecap="round"/>' +
    '<text x="52" y="35" font-family="system-ui, sans-serif" font-size="18" font-weight="700" fill="#fff">Boekhouding Peeters</text>' +
    '</svg>',
)}`;

export const sponsors: SponsorsDto = {
  sponsors: [{ name: 'Boekhouding Peeters', logoUrl: sponsorLogo, url: 'https://example.com/sponsor' }],
};

export const donationStats: DonationStatsDto = {
  totalContributions: 37,
  totalProcessed: 18452,
  processedToday: 214,
  maxProcessedLastWeek: 1310,
  invoicesRemaining: 0,
  activeCompanies: 1287,
  transactions: [],
  contributions: [
    { name: 'Bakkerij De Smet', message: 'Bedankt voor het gratis platform!', amount: 25, currency: 'EUR', date: '2026-11-03' },
    { name: 'Koffiebranderij Mokabon – Groothandel in koffie en thee', message: 'Merci', amount: 100, currency: 'EUR', date: '2026-10-28' },
  ],
};

export const openCollectiveStats: DonationStatsDto = {
  ...donationStats,
  totalContributions: 4,
  contributions: [],
  transactions: [
    { type: 'CREDIT', fromAccount: { name: 'Jan Peeters', slug: 'jan-peeters' }, amount: { value: 50, currency: 'EUR' }, createdAt: '2026-11-01T10:00:00Z' },
    { type: 'CREDIT', fromAccount: { name: '', slug: 'guest' }, amount: { value: 5, currency: 'EUR' }, createdAt: '2026-10-21T08:30:00Z' },
  ],
};

export interface DonationOptions {
  stats?: DonationStatsDto;
  sponsors?: SponsorsDto;
  sponsorOutcome?: 'invoice' | 'package' | 'duplicate' | 'failed' | 'pending';
}

export function donationMocks(options: DonationOptions = {}): IRegistration[] {
  const stats = options.stats ?? donationStats;
  const sponsorList = options.sponsors ?? sponsors;
  const sponsor = {
    invoice: resolved<SponsorInvoiceResponse>({ status: 'INVOICE_CREATED', message: '' }, 400),
    package: resolved<SponsorInvoiceResponse>({ status: 'PACKAGE_REQUESTED', message: 'Thank you. We will contact you within two working days to agree on a sponsor package.' }, 400),
    duplicate: rejected(409),
    failed: rejected(502),
    pending: pending<SponsorInvoiceResponse>(),
  }[options.sponsorOutcome ?? 'invoice'];
  return [
    provide(StatisticsService, stub<StatisticsService>('StatisticsService', { getDonationStats: resolved(stats) })),
    provide(SponsorService, stub<SponsorService>('SponsorService', { getSponsors: resolved(sponsorList), sponsor })),
  ];
}

export const welcomeNotifications: WelcomeNotificationsResponse = {
  peppolId: BAKERY_PEPPOL_ID,
  companyGroup: 'USER',
  notifications: [
    {
      id: 101,
      notificationGroup: 'USER',
      htmlCodeEn:
        '<h3>Welcome to Let’s Peppol</h3><p>Your company is registered on the Peppol network. Invoices your customers send to your enterprise number now arrive here.</p><ul><li>Send your first invoice from <strong>Invoices</strong>.</li><li>Add your bank account under <strong>Settings</strong> so customers can pay by QR code.</li></ul>',
      htmlCodeNl:
        '<h3>Welkom bij Let’s Peppol</h3><p>Je onderneming is geregistreerd op het Peppol-netwerk. Facturen die klanten naar je ondernemingsnummer sturen, komen nu hier binnen.</p><ul><li>Verstuur je eerste factuur via <strong>Facturen</strong>.</li><li>Voeg je bankrekening toe bij <strong>Instellingen</strong> zodat klanten met een QR-code kunnen betalen.</li></ul>',
      htmlCodeFr:
        '<h3>Bienvenue sur Let’s Peppol</h3><p>Votre entreprise est enregistrée sur le réseau Peppol. Les factures que vos clients envoient à votre numéro d’entreprise arrivent désormais ici.</p><ul><li>Envoyez votre première facture depuis <strong>Factures</strong>.</li><li>Ajoutez votre compte bancaire dans <strong>Paramètres</strong> pour que vos clients puissent payer par code QR.</li></ul>',
      htmlCodeDe:
        '<h3>Willkommen bei Let’s Peppol</h3><p>Ihr Unternehmen ist im Peppol-Netzwerk registriert. Rechnungen, die Kunden an Ihre Unternehmensnummer senden, kommen jetzt hier an.</p><ul><li>Senden Sie Ihre erste Rechnung unter <strong>Rechnungen</strong>.</li><li>Hinterlegen Sie Ihr Bankkonto unter <strong>Einstellungen</strong>, damit Kunden per QR-Code zahlen können.</li></ul>',
    },
    {
      id: 102,
      notificationGroup: 'ONCE',
      htmlCodeEn:
        '<h3>New: discounts and extra costs on invoice lines</h3><p>You can now add a discount or a surcharge to a single line or to the whole invoice. The totals and VAT breakdown update as you type.</p>',
      htmlCodeNl:
        '<h3>Nieuw: kortingen en extra kosten op factuurlijnen</h3><p>Je kunt nu een korting of toeslag toevoegen aan één lijn of aan de hele factuur. De totalen en btw-opsplitsing worden meteen bijgewerkt.</p>',
      htmlCodeFr: '',
      htmlCodeDe: '',
    },
    {
      id: 103,
      notificationGroup: 'ONCE',
      htmlCodeEn:
        '<h3>Planned maintenance</h3><p>On Sunday 16 November between 06:00 and 08:00 sending can be delayed by a few minutes. Invoices you send in that window are queued and delivered automatically.</p>',
      htmlCodeNl:
        '<h3>Gepland onderhoud</h3><p>Op zondag 16 november tussen 06:00 en 08:00 kan het verzenden enkele minuten vertraging oplopen. Facturen die je in dat venster verstuurt, worden in de wachtrij gezet en automatisch afgeleverd.</p>',
      htmlCodeFr:
        '<h3>Maintenance planifiée</h3><p>Le dimanche 16 novembre entre 06:00 et 08:00, l’envoi peut prendre quelques minutes de retard. Les factures envoyées pendant ce créneau sont mises en file d’attente et livrées automatiquement.</p>',
      htmlCodeDe:
        '<h3>Geplante Wartung</h3><p>Am Sonntag, 16. November, kann sich der Versand zwischen 06:00 und 08:00 Uhr um einige Minuten verzögern. Rechnungen aus diesem Zeitraum werden in die Warteschlange gestellt und automatisch zugestellt.</p>',
    },
  ],
};

export function welcomeNotificationMocks(response: WelcomeNotificationsResponse = welcomeNotifications): IRegistration[] {
  localStorage.removeItem('seenNotification');
  localStorage.removeItem(`welcomeNotifications.seen.${response.peppolId}`);
  return [
    provide(
      WelcomeNotificationService,
      stub<WelcomeNotificationService>('WelcomeNotificationService', { getWelcomeNotifications: resolved(response, 200) }),
    ),
  ];
}

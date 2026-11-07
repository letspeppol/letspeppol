import type { IRegistration } from '@aurelia/kernel';
import type { RouteNode } from '@aurelia/router';
import { waitFor } from 'storybook/test';
import { AccountService, type CurrentAccount } from '../../../src/services/kyc/account-service';
import { PasskeyService, type PasskeyDto } from '../../../src/services/kyc/passkey-service';
import { TotpService, type TotpSetupResponse } from '../../../src/services/kyc/totp-service';
import { PasswordService } from '../../../src/services/kyc/password-service';
import { CompanyUserService, type CompanyUserDto, type InvitationInfo } from '../../../src/services/kyc/company-user-service';
import {
  RegistrationService,
  type KycCompanyResponse,
  type TokenVerificationResponse,
} from '../../../src/services/kyc/registration-service';
import {
  BrowserAuthenticationError,
  BrowserAuthenticationService,
  type BrowserAuthenticationSession,
  type BrowserAuthenticationStatus,
} from '../../../src/services/kyc/browser-authentication-service';
import { LoginService } from '../../../src/services/app/login-service';
import { OwnershipService } from '../../../src/services/app/ownership-service';
import {
  CompanyService,
  type CompanyDto,
  type PeppolRegistrationDto,
  type PeppolSettings,
} from '../../../src/services/app/company-service';
import { StatisticsService, type DonationStatsDto } from '../../../src/services/app/statistics-service';
import { SponsorService } from '../../../src/services/app/sponsor-service';
import { PeppolDirService, type PeppolDirectoryResponse } from '../../../src/services/peppol/peppol-dir-service';
import { ALL_PERMISSIONS, CompanyPermission, normalizePermissionMask } from '../../../src/services/app/company-permission';
import { BAKERY_PEPPOL_ID, bakery, currentAccount } from './fixtures';
import { routerMocks } from './session';
import { pending, provide, resolved, stub } from './stub';

const DAY = 24 * 60 * 60 * 1000;

function daysFromNow(days: number): string {
  return new Date(Date.now() + days * DAY).toISOString();
}

export function failure(status: number, body?: unknown): () => Promise<never> {
  return () => Promise.reject(new Response(body === undefined ? null : typeof body === 'string' ? body : JSON.stringify(body), {
    status,
    headers: typeof body === 'object' ? { 'Content-Type': 'application/json' } : undefined,
  }));
}

export function routeNode(query = '', data: Record<string, unknown> = {}): RouteNode {
  return { queryParams: new URLSearchParams(query), data, params: {} } as unknown as RouteNode;
}

interface RoutedPage {
  loading(params: Record<string, string>, next: RouteNode): void;
}

export function pageTemplate(tag: string, attributes = ''): string {
  return `<${tag} submit.trigger="$event.preventDefault()"${attributes}></${tag}>`;
}

export function routedPage(tag: string, query = '', data: Record<string, unknown> = {}) {
  return {
    template: pageTemplate(tag, ' component.ref="page"'),
    props: {
      bound(this: { page: RoutedPage }) {
        this.page.loading({}, routeNode(query, data));
      },
    },
  };
}

export async function element<T extends Element = HTMLElement>(root: HTMLElement, selector: string): Promise<T> {
  return waitFor(() => {
    const found = root.querySelector<T>(selector);
    if (!found) {
      throw new Error(`${selector} is not rendered yet`);
    }
    return found;
  }, { timeout: 5000 });
}

export async function visibleElement<T extends HTMLElement = HTMLElement>(root: HTMLElement, selector: string): Promise<T> {
  return waitFor(() => {
    const found = [...root.querySelectorAll<T>(selector)].find((candidate) => candidate.offsetParent !== null);
    if (!found) {
      throw new Error(`${selector} is not visible yet`);
    }
    return found;
  }, { timeout: 5000 });
}

function fakeQrCode(size = 29): string {
  let seed = 20261105;
  const random = () => {
    seed = (seed * 16807) % 2147483647;
    return seed / 2147483647;
  };
  const finderOrigins = [[0, 0], [size - 7, 0], [0, size - 7]];
  const finder = (x: number, y: number): boolean | null => {
    for (const [ox, oy] of finderOrigins) {
      const dx = x - ox;
      const dy = y - oy;
      if (dx >= -1 && dx <= 7 && dy >= -1 && dy <= 7) {
        const ring = Math.max(Math.abs(dx - 3), Math.abs(dy - 3));
        return ring === 3 || ring <= 1;
      }
    }
    return null;
  };
  const modules: string[] = [];
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      const fixed = finder(x, y);
      const timing = (x === 6 || y === 6) ? (x + y) % 2 === 0 : null;
      const dark = fixed ?? timing ?? random() > 0.52;
      if (dark) {
        modules.push(`M${x} ${y}h1v1h-1z`);
      }
    }
  }
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="-4 -4 ${size + 8} ${size + 8}" shape-rendering="crispEdges">`
    + `<rect x="-4" y="-4" width="${size + 8}" height="${size + 8}" fill="#fff"/><path fill="#000" d="${modules.join('')}"/></svg>`;
  return `data:image/svg+xml,${encodeURIComponent(svg)}`;
}

export const accountProfile: CurrentAccount = {
  name: currentAccount.name,
  email: currentAccount.email,
};

export const passkeys: PasskeyDto[] = [
  { id: 11, displayName: 'MacBook Pro (Touch ID)', createdOn: '2026-03-14T09:12:00Z', lastUsedOn: '2026-11-04T08:41:00Z' },
  { id: 12, displayName: 'iPhone 15', createdOn: '2026-05-02T18:30:00Z', lastUsedOn: '2026-10-28T12:05:00Z' },
  { id: 13, displayName: 'YubiKey 5C NFC – reservesleutel in de kluis van de winkel', createdOn: '2026-09-20T07:55:00Z', lastUsedOn: null },
];

export const totpSetup: TotpSetupResponse = {
  secret: 'KRSXG5CTMVRXEZLUKN2XAZLSKNSWG4TF',
  qrCodeDataUri: fakeQrCode(),
};

export const recoveryCodes = ['K7Q2M9XD', 'P4WJ8ZRA', 'T2N6HC5V', 'B9XK3MLE', 'R5DQ7WTY', 'H8VF2NJP', 'C3ZL6QMS', 'W7AT4KXG'];

export interface AccountServicesOptions {
  account?: CurrentAccount | 'loading';
  passkeys?: PasskeyDto[];
  totpEnabled?: boolean;
  changePasswordFails?: boolean;
}

export function accountServices(options: AccountServicesOptions = {}): IRegistration[] {
  const account = options.account ?? accountProfile;
  const passkeyList = options.passkeys ?? passkeys;
  return [
    provide(AccountService, stub<AccountService>('AccountService', {
      getCurrentAccount: account === 'loading' ? pending() : resolved(account),
    })),
    provide(PasskeyService, stub<PasskeyService>('PasskeyService', {
      listPasskeys: account === 'loading' ? pending() : resolved(passkeyList),
      getRegistrationOptions: pending(),
      verifyRegistration: resolved(undefined),
      deletePasskey: resolved(undefined),
      renamePasskey: resolved(undefined),
    })),
    provide(TotpService, stub<TotpService>('TotpService', {
      getStatus: account === 'loading' ? pending() : resolved({ enabled: options.totpEnabled ?? false }),
      setup: resolved(totpSetup, 150),
      enable: async (code: string) => {
        if (code !== '482913') {
          throw new Response(null, { status: 400 });
        }
        return { recoveryCodes };
      },
      disable: resolved(undefined, 150),
    })),
    passwordService({ changePasswordFails: options.changePasswordFails }),
  ];
}

async function noContent(): Promise<Response> {
  await new Promise((resolve) => setTimeout(resolve, 200));
  return new Response(null, { status: 204 });
}

export interface PasswordServiceOptions {
  changePasswordFails?: boolean;
  forgotPasswordFails?: boolean;
  resetPasswordFails?: boolean;
}

export function passwordService(options: PasswordServiceOptions = {}): IRegistration {
  return provide(PasswordService, stub<PasswordService>('PasswordService', {
    kycApi: { httpClient: { isRequesting: false } },
    changePassword: options.changePasswordFails ? failure(500) : noContent,
    forgotPassword: options.forgotPasswordFails ? failure(500) : noContent,
    resetPassword: options.resetPasswordFails ? failure(400, { errorCode: 'token_invalid' }) : noContent,
  }));
}

function companyUser(user: Omit<CompanyUserDto, 'createdOn' | 'lastUsed' | 'invitationExpiresOn'> & Partial<CompanyUserDto>): CompanyUserDto {
  return {
    createdOn: '2026-02-01T10:00:00Z',
    lastUsed: '2026-11-04T16:20:00Z',
    invitationExpiresOn: null,
    ...user,
    permissionMask: normalizePermissionMask(user.permissionMask),
  };
}

export const companyUsers: CompanyUserDto[] = [
  companyUser({
    id: 1,
    name: 'Pieter Van den Broeck',
    email: 'pieter@bakkerijdesmet.be',
    type: 'USER',
    status: 'ACTIVE',
    permissionMask: CompanyPermission.INVOICE_SEND | CompanyPermission.PARTNER_MANAGE | CompanyPermission.PRODUCT_MANAGE,
  }),
  companyUser({
    id: 2,
    name: 'Lotte De Smet',
    email: 'lotte@bakkerijdesmet.be',
    type: 'ADMIN',
    status: 'ACTIVE',
    permissionMask: ALL_PERMISSIONS,
  }),
  companyUser({
    id: 3,
    name: 'Annelies Verstraete-Vanhoutte (Boekhoudkantoor Verstraete & Partners)',
    email: 'annelies.verstraete-vanhoutte@boekhoudkantoor-verstraete.be',
    type: 'AFFILIATE',
    status: 'ACTIVE',
    permissionMask: CompanyPermission.INVOICE_STATUS | CompanyPermission.INVOICE_EXPORT,
  }),
  companyUser({
    id: 4,
    name: 'Sofie Claes',
    email: 'sofie.claes@bakkerijdesmet.be',
    type: 'USER',
    status: 'INVITED',
    permissionMask: CompanyPermission.INVOICE_DRAFT,
    invitationExpiresOn: daysFromNow(6),
  }),
  companyUser({
    id: 5,
    name: 'Jonas Peeters',
    email: 'jonas@peeters-logistiek.be',
    type: 'USER',
    status: 'INVITED',
    permissionMask: CompanyPermission.INVOICE_READ,
    invitationExpiresOn: daysFromNow(-3),
  }),
  companyUser({
    id: 6,
    name: 'Marc Wouters',
    email: 'marc.wouters@bakkerijdesmet.be',
    type: 'USER',
    status: 'SUSPENDED',
    permissionMask: CompanyPermission.INVOICE_READ | CompanyPermission.PRODUCT_MANAGE,
  }),
];

export const invitationForNewAccount: InvitationInfo = {
  email: 'sofie.claes@bakkerijdesmet.be',
  name: 'Sofie Claes',
  companyName: bakery.name,
  peppolId: BAKERY_PEPPOL_ID,
  passwordRequired: true,
};

export const invitationForExistingAccount: InvitationInfo = {
  ...invitationForNewAccount,
  email: 'annelies.verstraete-vanhoutte@boekhoudkantoor-verstraete.be',
  name: 'Annelies Verstraete-Vanhoutte',
  passwordRequired: false,
};

export interface CompanyUserServiceOptions {
  users?: CompanyUserDto[] | 'loading' | 'failed';
  inviteError?: string;
  invitation?: InvitationInfo | { errorCode: string };
}

export function companyUserService(options: CompanyUserServiceOptions = {}): IRegistration {
  const users = options.users ?? companyUsers;
  const byId = (id: number) => (Array.isArray(users) ? users : companyUsers).find((user) => user.id === id) ?? companyUsers[0];
  const invitation = options.invitation ?? invitationForNewAccount;
  return provide(CompanyUserService, stub<CompanyUserService>('CompanyUserService', {
    getUsers: users === 'loading' ? pending() : users === 'failed' ? failure(503) : resolved(users, 150),
    inviteUser: options.inviteError
      ? failure(409, { errorCode: options.inviteError })
      : async (request: { email: string; name: string; permissionMask: number }) => companyUser({
        id: 99,
        type: 'USER',
        status: 'INVITED',
        invitationExpiresOn: daysFromNow(7),
        ...request,
      }),
    updatePermissions: async (id: number, permissionMask: number) => ({ ...byId(id), permissionMask }),
    suspendUser: async (id: number) => ({ ...byId(id), status: 'SUSPENDED' }),
    reactivateUser: async (id: number) => ({ ...byId(id), status: 'ACTIVE' }),
    resendInvitation: resolved(undefined, 200),
    removeUser: resolved(undefined, 200),
    verifyInvitation: 'errorCode' in invitation ? failure(410, invitation) : resolved(invitation, 300),
    acceptInvitation: resolved(undefined, 200),
  }));
}

export const kycCompany: KycCompanyResponse = {
  id: 42,
  peppolId: '0208:0798765432',
  identifier: '0798765432',
  vatNumber: 'BE0798765432',
  name: 'Loodgieterij Janssens BV',
  street: 'Stationsstraat 41',
  city: 'Leuven',
  postalCode: '3000',
  hasAdmin: false,
  directors: [
    { id: 1, name: 'Tom Janssens' },
    { id: 2, name: 'Els Janssens-Maes' },
    { id: 3, name: 'Bart Vandenberghe-Van Hoorebeke' },
  ],
};

export const kycCompanyWithAdmin: KycCompanyResponse = {
  ...kycCompany,
  peppolId: bakery.peppolId,
  identifier: bakery.identifier,
  vatNumber: bakery.vatNumber ?? '',
  name: bakery.name,
  street: 'Kerkstraat 12',
  city: 'Gent',
  postalCode: '9000',
  hasAdmin: true,
};

export const tokenVerification: TokenVerificationResponse = {
  email: 'tom@loodgieterijjanssens.be',
  accountExists: false,
  accountVerified: false,
  directorSigned: false,
  requestedType: 'ADMIN',
  company: kycCompany,
};

export interface RegistrationServiceOptions {
  company?: KycCompanyResponse | 'not-found';
  confirmFails?: boolean;
  token?: TokenVerificationResponse | 'loading' | 'invalid';
  registerCompany?: () => Promise<boolean>;
}

const contractPreview = `data:text/html;charset=utf-8,${encodeURIComponent(
  '<body style="margin:0;padding:32px;font:15px/1.6 system-ui,sans-serif;color:#1b2440;background:#fff">'
  + '<h1 style="font-size:20px">Overeenkomst Let’s Peppol</h1>'
  + '<p>Tussen de vzw Let’s Peppol en Loodgieterij Janssens BV, vertegenwoordigd door Tom Janssens.</p>'
  + '<h2 style="font-size:16px">Artikel 1. Voorwerp</h2>'
  + '<p>Deze overeenkomst regelt het verzenden en ontvangen van elektronische facturen via het Peppol-netwerk.</p>'
  + '<h2 style="font-size:16px">Artikel 2. Duur</h2>'
  + '<p>De overeenkomst geldt voor onbepaalde duur en kan op elk moment worden opgezegd.</p></body>',
)}`;

export function registrationService(options: RegistrationServiceOptions = {}): IRegistration {
  const company = options.company ?? kycCompany;
  const token = options.token ?? tokenVerification;
  return provide(RegistrationService, stub<RegistrationService>('RegistrationService', {
    getCompany: company === 'not-found' ? failure(404) : resolved(company, 250),
    confirmCompany: options.confirmFails ? failure(409) : resolved({}, 250),
    verifyToken: token === 'loading' ? pending() : token === 'invalid' ? failure(400) : resolved(token, 200),
    prepareSign: pending(),
    getContractUrl: () => contractPreview,
    finalizeSign: pending(),
    verifyAccount: pending(),
    registerCompany: options.registerCompany ?? resolved(true, 200),
    unregisterCompany: resolved(false, 200),
    downloadSignedContract: async () => new Response(new Blob(['%PDF-1.7'], { type: 'application/pdf' })),
  }));
}

export function peppolDirService(registeredElsewhere = false): IRegistration {
  const response = {
    'total-result-count': registeredElsewhere ? 1 : 0,
    matches: registeredElsewhere
      ? [{ participantID: { scheme: 'iso6523-actorid-upis', value: kycCompany.peppolId }, entities: [{ name: [{ name: kycCompany.name }], countryCode: 'BE' }] }]
      : [],
  } as unknown as PeppolDirectoryResponse;
  return provide(PeppolDirService, stub<PeppolDirService>('PeppolDirService', {
    findByParticipant: resolved(response, 100),
  }));
}

export interface CompanySettingsOptions {
  company?: CompanyDto;
  registration?: Partial<PeppolRegistrationDto>;
  settings?: PeppolSettings | 'failed';
}

export function companySettingsService(options: CompanySettingsOptions = {}): IRegistration {
  const company = options.company ?? bakery;
  const registration: PeppolRegistrationDto = {
    peppolId: company.peppolId,
    peppolActive: company.peppolActive,
    accessPoint: company.peppolActive ? 'RECOMMAND' : 'NONE',
    defaultAccessPoint: 'RECOMMAND',
    ...options.registration,
  };
  const settings = options.settings ?? { receiveDocuments: company.peppolActive };
  const peppolSettings = settings === 'failed' ? failure(502) : resolved(settings, 150);
  return provide(CompanyService, stub<CompanyService>('CompanyService', {
    myCompany: structuredClone(company),
    getAndSetMyCompanyForToken: resolved(company),
    updateCompany: async (dto: CompanyDto) => dto,
    getPeppolRegistration: resolved(registration, 100),
    getPeppolSettings: peppolSettings,
    getReceivingAvailability: peppolSettings,
    updatePeppolSettings: async (next: PeppolSettings) => next,
  }));
}

const donationStats: DonationStatsDto = {
  totalContributions: 4,
  totalProcessed: 48210,
  processedToday: 312,
  maxProcessedLastWeek: 1904,
  invoicesRemaining: 15200,
  activeCompanies: 1284,
  transactions: [],
  contributions: [
    { name: 'Koffiebranderij Mokabon', message: 'Merci!', amount: 50, currency: 'EUR', date: '2026-11-02T10:00:00Z' },
    { name: 'Brasserie Het Anker', message: 'Goed bezig', amount: 25, currency: 'EUR', date: '2026-10-29T15:30:00Z' },
  ],
};

export function donationMocks(): IRegistration[] {
  return [
    provide(StatisticsService, stub<StatisticsService>('StatisticsService', {
      getDonationStats: resolved(donationStats),
    })),
    provide(SponsorService, stub<SponsorService>('SponsorService', {
      getSponsors: resolved({ sponsors: [] }),
    })),
  ];
}

export interface BrowserAuthenticationOptions {
  session?: BrowserAuthenticationStatus | 'loading' | 'unavailable';
  passwordResult?: BrowserAuthenticationStatus | number;
}

export function browserAuthentication(options: BrowserAuthenticationOptions = {}): IRegistration {
  const status = options.session ?? 'anonymous';
  const session: BrowserAuthenticationSession = {
    status: status === 'loading' || status === 'unavailable' ? 'anonymous' : status,
    csrfToken: 'storybook-csrf',
    csrfHeaderName: 'X-XSRF-TOKEN',
    csrfParameterName: '_csrf',
  };
  const passwordResult = options.passwordResult ?? 401;
  return provide(BrowserAuthenticationService, stub<BrowserAuthenticationService>('BrowserAuthenticationService', {
    getSession: status === 'loading' ? pending() : status === 'unavailable' ? () => Promise.reject(new TypeError('Failed to fetch')) : resolved(session, 150),
    authenticateWithPassword: async () => {
      await new Promise((resolve) => setTimeout(resolve, 200));
      if (typeof passwordResult === 'number') {
        throw new BrowserAuthenticationError(passwordResult);
      }
      return passwordResult;
    },
    getPasskeyAuthenticationOptions: pending(),
    verifyPasskey: pending(),
    verifyTotp: async () => {
      throw new BrowserAuthenticationError(401);
    },
    invalidateSession: () => undefined,
  }));
}

export interface AnonymousOptions {
  path?: string;
  authorizationFailure?: string;
}

export function anonymousMocks(options: AnonymousOptions = {}): IRegistration[] {
  const loginService = stub<LoginService>('LoginService', {
    authenticated: false,
    ensureAuthenticated: async () => false,
    logout: () => undefined,
    completeLogin: async () => (options.authorizationFailure
      ? { authorized: false, reason: options.authorizationFailure }
      : { authorized: true }),
    getPostLoginPath: () => `/${BAKERY_PEPPOL_ID}/dashboard`,
    getCurrentOwnershipRoute: (path: string) => path,
  });
  const ownershipService = stub<OwnershipService>('OwnershipService', {
    ownerships: [],
    hasCompany: false,
    admin: false,
    getCurrentAccountEmail: () => null,
  });
  return [
    provide(LoginService, loginService),
    provide(OwnershipService, ownershipService),
    ...routerMocks(options.path ?? '/login'),
  ];
}

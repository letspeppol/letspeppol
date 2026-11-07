import { ICurrentRoute, IRouter, IRouterEvents, Router } from '@aurelia/router';
import type { IRegistration } from '@aurelia/kernel';
import { LoginService } from '../../../src/services/app/login-service';
import { OwnershipService, type OwnershipSummary } from '../../../src/services/app/ownership-service';
import { CompanyService, type CompanyDto } from '../../../src/services/app/company-service';
import { AccountCacheService } from '../../../src/services/app/account-cache-service';
import { ALL_PERMISSIONS, flagsFromMask, normalizePermissionMask } from '../../../src/services/app/company-permission';
import { bakery, currentAccount, ownerships as allOwnerships } from './fixtures';
import { provide, stub } from './stub';

export interface SessionOptions {
  company?: CompanyDto | null;
  ownerships?: OwnershipSummary[];
  admin?: boolean;
  permissionMask?: number;
  path?: string;
}

export function sessionMocks(options: SessionOptions = {}): IRegistration[] {
  const company = options.company === undefined ? bakery : options.company;
  const ownerships = options.ownerships ?? (company ? allOwnerships : []);
  const admin = options.admin ?? true;
  const mask = normalizePermissionMask(options.permissionMask ?? ALL_PERMISSIONS);
  const current = company ? ownerships.find((o) => o.peppolId === company.peppolId) ?? ownerships[0] : undefined;
  const ownershipKey = (peppolId: string, type: string) => `${peppolId}::${type}`;
  const route = (path: string) => (company ? `/${company.peppolId}${path}` : path);

  const loginService = stub<LoginService>('LoginService', {
    authenticated: true,
    token: 'storybook-token',
    ensureAuthenticated: async () => true,
    getCurrentOwnershipRoute: route,
    getPostLoginPath: () => route('/dashboard'),
    swapOwnership: async () => undefined,
    logout: () => undefined,
    refreshToken: async () => true,
    rememberCurrentNavigation: () => undefined,
    getTokenExpiryDateInSeconds: () => Math.floor(Date.now() / 1000) + 3600,
    getCurrentDateInSeconds: () => Math.floor(Date.now() / 1000),
  });

  const ownershipService = stub<OwnershipService>('OwnershipService', {
    ownerships,
    permissionMask: company ? mask : 0,
    admin: !!company && admin,
    hasCompany: !!company,
    granted: flagsFromMask(company ? mask : 0),
    can: (permission: number) => !!company && (mask & permission) !== 0,
    loadOwnerships: async () => ownerships,
    getCachedOwnerships: () => ownerships,
    clearOwnerships: () => undefined,
    onTokenChanged: () => undefined,
    getCurrentAccountId: () => currentAccount.id,
    getCurrentAccountEmail: () => currentAccount.email,
    getCurrentOwnershipKey: () => (current ? ownershipKey(current.peppolId, current.type) : null),
    getCurrentOwnershipType: () => current?.type ?? null,
    getCurrentPeppolId: () => current?.peppolId ?? null,
    getRememberedOwnershipType: () => current?.type ?? null,
    rememberOwnership: () => undefined,
    getOwnershipKey: ownershipKey,
    selectOwnership: async () => undefined,
    refreshCompanyContext: async () => undefined,
  });

  const companyService = stub<CompanyService>('CompanyService', {
    myCompany: company ? structuredClone(company) : undefined,
    getAndSetMyCompanyForToken: async () => structuredClone(company),
    updateCompany: async (dto: CompanyDto) => dto,
    getPeppolRegistration: async () => ({
      peppolId: company?.peppolId ?? '',
      peppolActive: company?.peppolActive ?? false,
      accessPoint: 'Let’s Peppol',
      defaultAccessPoint: 'Let’s Peppol',
    }),
    getPeppolSettings: async () => ({ receiveDocuments: true }),
    getReceivingAvailability: async () => ({ receiveDocuments: true }),
    updatePeppolSettings: async (settings: unknown) => settings,
  });

  return [
    provide(LoginService, loginService),
    provide(OwnershipService, ownershipService),
    provide(CompanyService, companyService),
    provide(AccountCacheService, stub<AccountCacheService>('AccountCacheService', { clearAll: () => undefined })),
    ...routerMocks(options.path ?? route('/dashboard')),
  ];
}

export function routerMocks(path = '/dashboard'): IRegistration[] {
  const router = stub<Router>('Router', {
    load: async (target: unknown) => {
      console.info('[storybook mock] router.load', target);
      return true;
    },
    isActive: () => false,
  });
  const currentRoute = stub<ICurrentRoute>('ICurrentRoute', {
    path,
    url: path,
    title: '',
    query: new URLSearchParams(),
    parameterInformation: [],
  });
  const routerEvents = stub<IRouterEvents>('IRouterEvents', {
    subscribe: () => ({ dispose: () => undefined }),
    publish: () => undefined,
  });
  return [
    provide(Router, router),
    provide(IRouter, router),
    provide(ICurrentRoute, currentRoute),
    provide(IRouterEvents, routerEvents),
  ];
}

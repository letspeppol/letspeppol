export enum CompanyPermission {
    INVOICE_READ = 1,
    INVOICE_DRAFT = 2,
    INVOICE_SEND = 4,
    INVOICE_STATUS = 8,
    INVOICE_EXPORT = 16,
    PARTNER_MANAGE = 32,
    PRODUCT_MANAGE = 64,
    COMPANY_SETTINGS = 128,
}

export type CompanyPermissionName = keyof typeof CompanyPermission;

export type PermissionFlags = Record<CompanyPermissionName, boolean>;

export const PERMISSION_COLUMNS: CompanyPermissionName[] = [
    'INVOICE_READ',
    'INVOICE_DRAFT',
    'INVOICE_SEND',
    'INVOICE_STATUS',
    'INVOICE_EXPORT',
    'PARTNER_MANAGE',
    'PRODUCT_MANAGE',
    'COMPANY_SETTINGS',
];

export const ALL_PERMISSIONS = PERMISSION_COLUMNS.reduce((mask, name) => mask | CompanyPermission[name], 0);

const MASK_WITHOUT_CLAIM = new Map<string, number>([
    ['USER', 0],
    ['USER_DRAFT', CompanyPermission.INVOICE_READ | CompanyPermission.INVOICE_DRAFT],
    ['USER_READ', CompanyPermission.INVOICE_READ],
]);
const READ_DEPENDENTS = CompanyPermission.INVOICE_DRAFT | CompanyPermission.INVOICE_STATUS | CompanyPermission.INVOICE_EXPORT;

export function hasPermission(mask: number, permission: CompanyPermission): boolean {
    return (mask & permission) !== 0;
}

export function impliedPermissions(mask: number): number {
    let implied = 0;
    if (hasPermission(mask, CompanyPermission.INVOICE_SEND)) {
        implied |= CompanyPermission.INVOICE_DRAFT;
    }
    if (((mask | implied) & READ_DEPENDENTS) !== 0) {
        implied |= CompanyPermission.INVOICE_READ;
    }
    return implied;
}

export function normalizePermissionMask(mask: number): number {
    const known = mask & ALL_PERMISSIONS;
    return known | impliedPermissions(known);
}

export function flagsFromMask(mask: number): PermissionFlags {
    const flags = {} as PermissionFlags;
    for (const name of PERMISSION_COLUMNS) {
        flags[name] = hasPermission(mask, CompanyPermission[name]);
    }
    return flags;
}

export function maskFromFlags(flags: Partial<PermissionFlags>): number {
    return PERMISSION_COLUMNS.reduce((mask, name) => flags[name] ? mask | CompanyPermission[name] : mask, 0);
}

export function effectivePermissionMask(claim: unknown, accountType: string | null | undefined): number {
    if (typeof claim === 'number' && Number.isInteger(claim)) {
        return claim & ALL_PERMISSIONS;
    }
    return MASK_WITHOUT_CLAIM.get(accountType ?? '') ?? ALL_PERMISSIONS;
}

import {describe, expect, it} from 'vitest';
import {
    ALL_PERMISSIONS,
    CompanyPermission,
    effectivePermissionMask,
    flagsFromMask,
    hasPermission,
    impliedPermissions,
    maskFromFlags,
    normalizePermissionMask,
    PERMISSION_COLUMNS,
} from '../../src/services/app/company-permission';

describe('company permissions', () => {
    it('keeps the bit values agreed with the backends', () => {
        expect(PERMISSION_COLUMNS.map(name => [name, CompanyPermission[name]])).toEqual([
            ['INVOICE_READ', 1],
            ['INVOICE_DRAFT', 2],
            ['INVOICE_SEND', 4],
            ['INVOICE_STATUS', 8],
            ['INVOICE_EXPORT', 16],
            ['PARTNER_MANAGE', 32],
            ['PRODUCT_MANAGE', 64],
            ['COMPANY_SETTINGS', 128],
        ]);
        expect(ALL_PERMISSIONS).toBe(255);
    });

    it('adds the permissions another permission depends on', () => {
        expect(normalizePermissionMask(CompanyPermission.INVOICE_SEND)).toBe(7);
        expect(normalizePermissionMask(CompanyPermission.INVOICE_DRAFT)).toBe(3);
        expect(normalizePermissionMask(CompanyPermission.INVOICE_STATUS)).toBe(9);
        expect(normalizePermissionMask(CompanyPermission.INVOICE_EXPORT)).toBe(17);
        expect(normalizePermissionMask(CompanyPermission.PARTNER_MANAGE)).toBe(32);
        expect(normalizePermissionMask(0)).toBe(0);
    });

    it('drops bits it does not know', () => {
        expect(normalizePermissionMask(256 | CompanyPermission.INVOICE_READ)).toBe(1);
    });

    it('reports which permissions are held only because of another one', () => {
        expect(impliedPermissions(CompanyPermission.INVOICE_SEND)).toBe(3);
        expect(impliedPermissions(CompanyPermission.INVOICE_DRAFT)).toBe(1);
        expect(impliedPermissions(CompanyPermission.INVOICE_READ | CompanyPermission.COMPANY_SETTINGS)).toBe(0);
    });

    it('converts between a mask and named flags', () => {
        const flags = flagsFromMask(37);

        expect(flags.INVOICE_READ).toBe(true);
        expect(flags.INVOICE_DRAFT).toBe(false);
        expect(flags.INVOICE_SEND).toBe(true);
        expect(flags.PARTNER_MANAGE).toBe(true);
        expect(maskFromFlags(flags)).toBe(37);
        expect(hasPermission(37, CompanyPermission.PRODUCT_MANAGE)).toBe(false);
    });

    it('uses the token claim when present', () => {
        expect(effectivePermissionMask(3, 'USER')).toBe(3);
        expect(effectivePermissionMask(0, 'ADMIN')).toBe(0);
        expect(effectivePermissionMask(511, 'USER')).toBe(255);
    });

    it('treats a token without the claim as full access unless it belongs to a user', () => {
        expect(effectivePermissionMask(undefined, 'USER')).toBe(0);
        expect(effectivePermissionMask(undefined, 'ADMIN')).toBe(255);
        expect(effectivePermissionMask(undefined, 'AFFILIATE')).toBe(255);
        expect(effectivePermissionMask('7', 'USER')).toBe(0);
    });
});

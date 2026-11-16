import {describe, expect, it, vi} from 'vitest';
import {CompanyUserDto} from '../../src/services/kyc/company-user-service';
import {toUserRow, UserRow, Users} from '../../src/users/users';

function user(overrides: Partial<CompanyUserDto> = {}): CompanyUserDto {
    return {
        id: 7,
        name: 'An Peeters',
        email: 'an@example.be',
        type: 'USER',
        status: 'ACTIVE',
        permissionMask: 1,
        createdOn: '2026-10-01T08:00:00Z',
        lastUsed: '2026-10-02T08:00:00Z',
        invitationExpiresOn: null,
        ...overrides,
    };
}

function createUsers(rows: UserRow[] = []) {
    const companyUserService = {
        getUsers: vi.fn(),
        updatePermissions: vi.fn(),
        suspendUser: vi.fn(),
        reactivateUser: vi.fn(),
        resendInvitation: vi.fn(),
        removeUser: vi.fn(),
    };
    const publish = vi.fn();
    const showConfirmationModal = vi.fn();
    const users = Object.create(Users.prototype) as Users;
    Object.assign(users, {
        companyUserService,
        ea: {publish},
        i18n: {tr: (key: string) => key},
        confirmationModalContext: {showConfirmationModal},
        rows,
        loadingUsers: false,
        loadFailed: false,
    });
    return {users, companyUserService, publish, showConfirmationModal};
}

describe('Users permission matrix', () => {
    it('sends the normalised mask for a toggled permission and shows what the server stored', async () => {
        const row = toUserRow(user({permissionMask: 1}));
        const {users, companyUserService} = createUsers([row]);
        companyUserService.updatePermissions.mockResolvedValue(user({permissionMask: 7}));

        await users.togglePermission(row, 'INVOICE_SEND', true);

        expect(companyUserService.updatePermissions).toHaveBeenCalledWith(7, 7);
        expect(row.user.permissionMask).toBe(7);
        expect(row.flags.INVOICE_SEND).toBe(true);
        expect(row.flags.INVOICE_DRAFT).toBe(true);
        expect(row.implied.INVOICE_DRAFT).toBe(true);
        expect(row.implied.INVOICE_READ).toBe(true);
        expect(row.saving).toBe(false);
    });

    it('removes only the unticked permission', async () => {
        const row = toUserRow(user({permissionMask: 7 | 32}));
        const {users, companyUserService} = createUsers([row]);
        companyUserService.updatePermissions.mockResolvedValue(user({permissionMask: 3 | 32}));

        await users.togglePermission(row, 'INVOICE_SEND', false);

        expect(companyUserService.updatePermissions).toHaveBeenCalledWith(7, 3 | 32);
        expect(row.flags.INVOICE_SEND).toBe(false);
        expect(row.implied.INVOICE_DRAFT).toBe(false);
        expect(row.implied.INVOICE_READ).toBe(true);
    });

    it('locks the row while a change is being saved', async () => {
        const row = toUserRow(user({permissionMask: 1}));
        const {users, companyUserService} = createUsers([row]);
        let finish: (updated: CompanyUserDto) => void = () => undefined;
        companyUserService.updatePermissions.mockReturnValue(new Promise<CompanyUserDto>(resolve => finish = resolve));

        const pending = users.togglePermission(row, 'PARTNER_MANAGE', true);
        expect(row.saving).toBe(true);
        expect(row.flags.PARTNER_MANAGE).toBe(true);
        await users.togglePermission(row, 'PRODUCT_MANAGE', true);
        finish(user({permissionMask: 33}));
        await pending;

        expect(companyUserService.updatePermissions).toHaveBeenCalledTimes(1);
        expect(row.saving).toBe(false);
    });

    it('puts the boxes back and warns when saving fails', async () => {
        const row = toUserRow(user({permissionMask: 3}));
        const {users, companyUserService, publish} = createUsers([row]);
        companyUserService.updatePermissions.mockRejectedValue(new Error('offline'));

        await users.togglePermission(row, 'INVOICE_EXPORT', true);

        expect(row.user.permissionMask).toBe(3);
        expect(row.flags.INVOICE_EXPORT).toBe(false);
        expect(row.flags.INVOICE_DRAFT).toBe(true);
        expect(row.saving).toBe(false);
        expect(publish).toHaveBeenCalledWith('alert', {alertType: 'Danger', text: 'alert.users.permissions-failed'});
    });

    it('never changes the administrator row', async () => {
        const row = toUserRow(user({type: 'ADMIN', permissionMask: 255}));
        const {users, companyUserService} = createUsers([row]);

        await users.togglePermission(row, 'INVOICE_SEND', false);

        expect(row.editable).toBe(false);
        expect(companyUserService.updatePermissions).not.toHaveBeenCalled();
        expect(row.flags.INVOICE_SEND).toBe(true);
    });

    it('lets the administrator change the permissions of an affiliate but not remove it', async () => {
        const row = toUserRow(user({type: 'AFFILIATE', permissionMask: 255}));
        const {users, companyUserService} = createUsers([row]);
        companyUserService.updatePermissions.mockResolvedValue(user({type: 'AFFILIATE', permissionMask: 127}));

        await users.togglePermission(row, 'COMPANY_SETTINGS', false);

        expect(companyUserService.updatePermissions).toHaveBeenCalledWith(7, 127);
        expect(row.flags.COMPANY_SETTINGS).toBe(false);
        expect(row.editable).toBe(true);
        expect(row.removable).toBe(false);
        expect(users.isAffiliate(row)).toBe(true);
        expect(toUserRow(user()).removable).toBe(true);
    });

    it('lists the administrator first', async () => {
        const {users, companyUserService} = createUsers();
        companyUserService.getUsers.mockResolvedValue([
            user({id: 2, name: 'Bram'}),
            user({id: 1, name: 'Chris', type: 'ADMIN', permissionMask: 255}),
            user({id: 3, name: 'Dana', status: 'INVITED'}),
        ]);

        await users.loadUsers();

        expect(users.rows.map(row => row.user.id)).toEqual([1, 2, 3]);
        expect(users.loadingUsers).toBe(false);
        expect(users.loadFailed).toBe(false);
    });

    it('reports a failed load without leaving stale rows', async () => {
        const {users, companyUserService} = createUsers([toUserRow(user())]);
        companyUserService.getUsers.mockRejectedValue(new Error('offline'));

        await users.loadUsers();

        expect(users.rows).toEqual([]);
        expect(users.loadFailed).toBe(true);
    });

    it('asks before suspending and applies the new status', async () => {
        const row = toUserRow(user());
        const {users, companyUserService, showConfirmationModal} = createUsers([row]);
        companyUserService.suspendUser.mockResolvedValue(user({status: 'SUSPENDED'}));

        users.confirmSuspend(row);
        expect(companyUserService.suspendUser).not.toHaveBeenCalled();
        await showConfirmationModal.mock.calls[0][2]();

        expect(companyUserService.suspendUser).toHaveBeenCalledWith(7);
        expect(row.user.status).toBe('SUSPENDED');
    });

    it('drops a removed user from the list', async () => {
        const kept = toUserRow(user({id: 1, type: 'ADMIN', permissionMask: 255}));
        const removed = toUserRow(user({id: 7}));
        const {users, companyUserService} = createUsers([kept, removed]);
        companyUserService.removeUser.mockResolvedValue(undefined);

        await users.remove(removed);

        expect(companyUserService.removeUser).toHaveBeenCalledWith(7);
        expect(users.rows).toEqual([kept]);
    });

    it('reloads the list and warns when an invited user was saved but the email failed', async () => {
        const {users, companyUserService, publish} = createUsers([]);
        const showModal = vi.fn();
        Object.assign(users, {inviteUserModal: {showModal}});
        companyUserService.getUsers.mockResolvedValue([user({status: 'INVITED'})]);

        users.showInviteModal();
        showModal.mock.calls[0][1]();
        await vi.waitFor(() => expect(users.rows).toHaveLength(1));

        expect(publish).toHaveBeenCalledWith('alert', expect.objectContaining({text: 'alert.users.invited-not-sent'}));
        expect(users.rows[0].user.status).toBe('INVITED');
    });

    it('flags an invitation whose link has expired', () => {
        const {users} = createUsers();

        expect(users.isInvitationExpired(toUserRow(user({status: 'INVITED', invitationExpiresOn: '2020-01-01T00:00:00Z'})))).toBe(true);
        expect(users.isInvitationExpired(toUserRow(user({status: 'INVITED', invitationExpiresOn: '2999-01-01T00:00:00Z'})))).toBe(false);
        expect(users.isInvitationExpired(toUserRow(user({status: 'ACTIVE', invitationExpiresOn: '2020-01-01T00:00:00Z'})))).toBe(false);
    });
});

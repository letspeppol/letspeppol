import {resolve} from "@aurelia/kernel";
import {IEventAggregator} from "aurelia";
import {I18N} from "@aurelia/i18n";
import {AlertType} from "../components/alert/alert";
import {ConfirmationModalContext} from "../components/confirmation/confirmation-modal-context";
import {toErrorResponse, toLocalizedErrorMessage} from "../app/util/error-response-handler";
import {CompanyUserDto, CompanyUserService} from "../services/kyc/company-user-service";
import {
    CompanyPermission,
    CompanyPermissionName,
    flagsFromMask,
    impliedPermissions,
    normalizePermissionMask,
    PERMISSION_COLUMNS,
    PermissionFlags,
} from "../services/app/company-permission";
import {InviteUserModal} from "./invite-user-modal";

const USER_TYPE = 'USER';
const AFFILIATE_TYPE = 'AFFILIATE';
const ADMIN_TYPE = 'ADMIN';

function isEditable(user: CompanyUserDto): boolean {
    return user.type === USER_TYPE || user.type === AFFILIATE_TYPE;
}

export interface UserRow {
    user: CompanyUserDto;
    editable: boolean;
    removable: boolean;
    saving: boolean;
    flags: PermissionFlags;
    implied: PermissionFlags;
}

export function toUserRow(user: CompanyUserDto): UserRow {
    return {
        user,
        editable: isEditable(user),
        removable: user.type === USER_TYPE,
        saving: false,
        flags: flagsFromMask(user.permissionMask),
        implied: flagsFromMask(impliedPermissions(user.permissionMask)),
    };
}

export class Users {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly i18n = resolve(I18N);
    private readonly companyUserService = resolve(CompanyUserService);
    private readonly confirmationModalContext = resolve(ConfirmationModalContext);
    readonly columns = PERMISSION_COLUMNS;
    inviteUserModal: InviteUserModal;
    rows: UserRow[] = [];
    loadingUsers = true;
    loadFailed = false;

    attached() {
        void this.loadUsers();
    }

    async loadUsers() {
        this.loadingUsers = true;
        this.loadFailed = false;
        try {
            this.rows = await this.fetchRows();
        } catch {
            this.rows = [];
            this.loadFailed = true;
        } finally {
            this.loadingUsers = false;
        }
    }

    isAdmin(row: UserRow): boolean {
        return row.user.type === ADMIN_TYPE;
    }

    isAffiliate(row: UserRow): boolean {
        return row.user.type === AFFILIATE_TYPE;
    }

    isInvitationExpired(row: UserRow): boolean {
        return row.user.status === 'INVITED'
            && !!row.user.invitationExpiresOn
            && new Date(row.user.invitationExpiresOn).getTime() < Date.now();
    }

    checkboxHint(row: UserRow, permission: CompanyPermissionName): string {
        if (this.isAdmin(row)) {
            return this.i18n.tr('users.hint.admin');
        }
        if (!row.editable) {
            return this.i18n.tr('users.hint.not-editable');
        }
        return row.implied[permission] ? this.i18n.tr('users.hint.implied') : '';
    }

    async togglePermission(row: UserRow, permission: CompanyPermissionName, checked: boolean) {
        if (!row.editable || row.saving) {
            return;
        }
        const previousMask = row.user.permissionMask;
        const bit = CompanyPermission[permission];
        const requestedMask = normalizePermissionMask(checked ? previousMask | bit : previousMask & ~bit);
        this.showMask(row, requestedMask);
        row.saving = true;
        try {
            this.applyUser(row, await this.companyUserService.updatePermissions(row.user.id, requestedMask));
        } catch (e: unknown) {
            this.showMask(row, previousMask);
            await this.alertFailure(e, this.i18n.tr('alert.users.permissions-failed'));
        } finally {
            row.saving = false;
        }
    }

    showInviteModal() {
        this.inviteUserModal.showModal(
            user => {
                this.rows.push(toUserRow(user));
                this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr('alert.users.invited', {email: user.email})});
            },
            () => {
                this.ea.publish('alert', {alertType: AlertType.Warning, text: this.i18n.tr('alert.users.invited-not-sent')});
                void this.loadUsers();
            }
        );
    }

    confirmSuspend(row: UserRow) {
        this.confirmationModalContext.showConfirmationModal(
            this.i18n.tr('users.suspend.title'),
            this.i18n.tr('users.suspend.confirm', {name: row.user.name}),
            () => this.suspend(row),
            undefined
        );
    }

    async suspend(row: UserRow) {
        await this.changeStatus(
            row,
            () => this.companyUserService.suspendUser(row.user.id),
            this.i18n.tr('alert.users.suspended'),
            this.i18n.tr('alert.users.suspend-failed')
        );
    }

    async reactivate(row: UserRow) {
        await this.changeStatus(
            row,
            () => this.companyUserService.reactivateUser(row.user.id),
            this.i18n.tr('alert.users.reactivated'),
            this.i18n.tr('alert.users.reactivate-failed')
        );
    }

    async resendInvitation(row: UserRow) {
        if (row.saving) {
            return;
        }
        row.saving = true;
        try {
            await this.companyUserService.resendInvitation(row.user.id);
            this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr('alert.users.invitation-resent', {email: row.user.email})});
            this.rows = await this.fetchRows().catch(() => this.rows);
        } catch (e: unknown) {
            await this.alertFailure(e, this.i18n.tr('alert.users.resend-failed'));
        } finally {
            row.saving = false;
        }
    }

    confirmRemove(row: UserRow) {
        this.confirmationModalContext.showConfirmationModal(
            this.i18n.tr('users.remove.title'),
            this.i18n.tr('users.remove.confirm', {name: row.user.name}),
            () => this.remove(row),
            undefined
        );
    }

    async remove(row: UserRow) {
        if (row.saving) {
            return;
        }
        row.saving = true;
        try {
            await this.companyUserService.removeUser(row.user.id);
            const index = this.rows.indexOf(row);
            if (index > -1) {
                this.rows.splice(index, 1);
            }
            this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr('alert.users.removed')});
        } catch (e: unknown) {
            await this.alertFailure(e, this.i18n.tr('alert.users.remove-failed'));
        } finally {
            row.saving = false;
        }
    }

    private async fetchRows(): Promise<UserRow[]> {
        const users = await this.companyUserService.getUsers();
        return users.map(toUserRow).sort((a, b) => Number(this.isAdmin(b)) - Number(this.isAdmin(a)));
    }

    private async changeStatus(row: UserRow, request: () => Promise<CompanyUserDto>, successText: string, failureText: string) {
        if (row.saving) {
            return;
        }
        row.saving = true;
        try {
            this.applyUser(row, await request());
            this.ea.publish('alert', {alertType: AlertType.Success, text: successText});
        } catch (e: unknown) {
            await this.alertFailure(e, failureText);
        } finally {
            row.saving = false;
        }
    }

    private applyUser(row: UserRow, user: CompanyUserDto) {
        row.user = user;
        row.editable = isEditable(user);
        row.removable = user.type === USER_TYPE;
        this.showMask(row, user.permissionMask);
    }

    private showMask(row: UserRow, mask: number) {
        row.flags = flagsFromMask(mask);
        row.implied = flagsFromMask(impliedPermissions(mask));
    }

    private async alertFailure(error: unknown, fallback: string) {
        const text = toLocalizedErrorMessage(await toErrorResponse(error), this.i18n, fallback);
        this.ea.publish('alert', {alertType: AlertType.Danger, text});
    }
}

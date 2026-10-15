import {resolve} from "@aurelia/kernel";
import {I18N} from "@aurelia/i18n";
import {validateEmail} from "../app/util/email-validation";
import {toErrorResponse, toLocalizedErrorMessage} from "../app/util/error-response-handler";
import {onModalEnter} from "../components/util/modal-keyboard";
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

const DEFAULT_PERMISSION_MASK = CompanyPermission.INVOICE_READ;
const INVITATION_NOT_SENT = 'invitation_not_sent';

export class InviteUserModal {
    private readonly companyUserService = resolve(CompanyUserService);
    private readonly i18n = resolve(I18N);
    readonly columns = PERMISSION_COLUMNS;
    open = false;
    submitting = false;
    name = '';
    email = '';
    permissionMask = DEFAULT_PERMISSION_MASK;
    flags: PermissionFlags = flagsFromMask(DEFAULT_PERMISSION_MASK);
    implied: PermissionFlags = flagsFromMask(0);
    errorText = '';
    private invitedFunction: (user: CompanyUserDto) => void = () => undefined;
    private savedWithoutEmailFunction: () => void = () => undefined;

    showModal(invitedFunction: (user: CompanyUserDto) => void, savedWithoutEmailFunction: () => void) {
        this.name = '';
        this.email = '';
        this.errorText = '';
        this.submitting = false;
        this.showMask(DEFAULT_PERMISSION_MASK);
        this.invitedFunction = invitedFunction;
        this.savedWithoutEmailFunction = savedWithoutEmailFunction;
        this.open = true;
    }

    closeModal() {
        this.open = false;
    }

    get canInvite(): boolean {
        return !!this.name?.trim() && validateEmail(this.email?.trim() ?? '') && !this.submitting;
    }

    togglePermission(permission: CompanyPermissionName, checked: boolean) {
        const bit = CompanyPermission[permission];
        this.showMask(checked ? this.permissionMask | bit : this.permissionMask & ~bit);
    }

    async invite() {
        if (!this.canInvite) {
            return;
        }
        this.submitting = true;
        this.errorText = '';
        try {
            const user = await this.companyUserService.inviteUser({
                email: this.email.trim(),
                name: this.name.trim(),
                permissionMask: this.permissionMask,
            });
            this.open = false;
            this.invitedFunction(user);
        } catch (e: unknown) {
            const errorResponse = await toErrorResponse(e);
            if (errorResponse?.errorCode === INVITATION_NOT_SENT) {
                this.open = false;
                this.savedWithoutEmailFunction();
                return;
            }
            this.errorText = toLocalizedErrorMessage(errorResponse, this.i18n, this.i18n.tr('alert.users.invite-failed'));
        } finally {
            this.submitting = false;
        }
    }

    onKeyDown(event: KeyboardEvent) {
        onModalEnter(event, () => this.invite());
    }

    private showMask(mask: number) {
        this.permissionMask = normalizePermissionMask(mask);
        this.flags = flagsFromMask(this.permissionMask);
        this.implied = flagsFromMask(impliedPermissions(this.permissionMask));
    }
}

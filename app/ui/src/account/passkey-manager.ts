import {resolve} from "@aurelia/kernel";
import {IEventAggregator} from "aurelia";
import {I18N} from "@aurelia/i18n";
import {PasskeyDto, PasskeyService} from "../services/kyc/passkey-service";
import {AlertType} from "../components/alert/alert";
import {ConfirmationModalContext} from "../components/confirmation/confirmation-modal-context";

export class PasskeyManager {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly passkeyService = resolve(PasskeyService);
    private readonly confirmationModalContext = resolve(ConfirmationModalContext);
    private readonly i18n: I18N = resolve(I18N);

    passkeys: PasskeyDto[] = [];
    supported = false;
    adding = false;
    newPasskeyName = '';
    editingId: number | null = null;
    editingName = '';

    get neverText() { return this.i18n.tr('passkey.last-used-never'); }
    get addText() { return this.i18n.tr('passkey.add'); }
    get registeringText() { return this.i18n.tr('passkey.registering'); }

    attaching() {
        this.supported = !!window.PublicKeyCredential;
        if (this.supported) {
            this.loadPasskeys();
        }
    }

    async loadPasskeys() {
        try {
            this.passkeys = await this.passkeyService.listPasskeys();
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('passkey.load-error')});
        }
    }

    async addPasskey() {
        if (!this.newPasskeyName.trim()) {
            this.newPasskeyName = this.i18n.tr('passkey.default-name');
        }
        const displayName = this.newPasskeyName.trim();

        try {
            this.adding = true;
            const {challengeToken, options} = await this.passkeyService.getRegistrationOptions(displayName);
            const credential = await navigator.credentials.create({publicKey: options}) as PublicKeyCredential;
            if (!credential) return;
            await this.passkeyService.verifyRegistration(credential, challengeToken, displayName);
            this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr('passkey.registered')});
            this.newPasskeyName = '';
            await this.loadPasskeys();
        } catch (e: any) {
            if (e.name !== 'AbortError' && e.name !== 'NotAllowedError') {
                console.error('Passkey registration failed:', e);
                this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('passkey.register-error')});
            }
        } finally {
            this.adding = false;
        }
    }

    confirmDelete(passkey: PasskeyDto) {
        this.confirmationModalContext.showConfirmationModal(
            this.i18n.tr('passkey.delete-title'),
            this.i18n.tr('passkey.delete-confirm', {name: passkey.displayName}),
            () => this.deletePasskey(passkey.id),
            undefined
        );
    }

    async deletePasskey(id: number) {
        try {
            await this.passkeyService.deletePasskey(id);
            this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr('passkey.deleted')});
            await this.loadPasskeys();
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('passkey.delete-error')});
        }
    }

    startRename(passkey: PasskeyDto) {
        this.editingId = passkey.id;
        this.editingName = passkey.displayName;
    }

    cancelRename() {
        this.editingId = null;
        this.editingName = '';
    }

    async saveRename(passkey: PasskeyDto) {
        if (!this.editingName.trim()) return;
        try {
            await this.passkeyService.renamePasskey(passkey.id, this.editingName.trim());
            passkey.displayName = this.editingName.trim();
            this.editingId = null;
            this.editingName = '';
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('passkey.rename-error')});
        }
    }
}

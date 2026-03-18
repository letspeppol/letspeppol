import {resolve} from "@aurelia/kernel";
import {IEventAggregator} from "aurelia";
import {PasskeyDto, PasskeyService} from "../services/kyc/passkey-service";
import {AlertType} from "../components/alert/alert";
import {ConfirmationModalContext} from "../components/confirmation/confirmation-modal-context";

export class PasskeyManager {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly passkeyService = resolve(PasskeyService);
    private readonly confirmationModalContext = resolve(ConfirmationModalContext);

    passkeys: PasskeyDto[] = [];
    supported = false;
    adding = false;
    newPasskeyName = '';
    editingId: number | null = null;
    editingName = '';

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
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to load passkeys"});
        }
    }

    async addPasskey() {
        if (!this.newPasskeyName.trim()) {
            this.newPasskeyName = 'My passkey';
        }
        const displayName = this.newPasskeyName.trim();

        try {
            this.adding = true;
            const {challengeToken, options} = await this.passkeyService.getRegistrationOptions(displayName);
            const credential = await navigator.credentials.create({publicKey: options}) as PublicKeyCredential;
            if (!credential) return;
            await this.passkeyService.verifyRegistration(credential, challengeToken, displayName);
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Passkey registered"});
            this.newPasskeyName = '';
            await this.loadPasskeys();
        } catch (e: any) {
            if (e.name !== 'AbortError' && e.name !== 'NotAllowedError') {
                console.error('Passkey registration failed:', e);
                this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to register passkey"});
            }
        } finally {
            this.adding = false;
        }
    }

    confirmDelete(passkey: PasskeyDto) {
        this.confirmationModalContext.showConfirmationModal(
            "Delete passkey",
            `Are you sure you want to delete the passkey "${passkey.displayName}"?`,
            () => this.deletePasskey(passkey.id),
            undefined
        );
    }

    async deletePasskey(id: number) {
        try {
            await this.passkeyService.deletePasskey(id);
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Passkey deleted"});
            await this.loadPasskeys();
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to delete passkey"});
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
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to rename passkey"});
        }
    }

    formatDate(dateStr: string | null): string {
        if (!dateStr) return 'Never';
        return new Date(dateStr).toLocaleDateString(undefined, {
            year: 'numeric', month: 'short', day: 'numeric',
            hour: '2-digit', minute: '2-digit'
        });
    }
}

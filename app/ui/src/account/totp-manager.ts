import {resolve} from "@aurelia/kernel";
import {IEventAggregator} from "aurelia";
import {I18N} from "@aurelia/i18n";
import {TotpService, TotpSetupResponse} from "../services/kyc/totp-service";
import {AlertType} from "../components/alert/alert";

type TotpState = 'loading' | 'disabled' | 'setup' | 'recovery-codes' | 'enabled' | 'disabling';

export class TotpManager {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly totpService = resolve(TotpService);
    private readonly i18n: I18N = resolve(I18N);

    state: TotpState = 'loading';
    setupData: TotpSetupResponse | null = null;
    recoveryCodes: string[] = [];
    verifyCode = '';
    disableCode = '';
    verifyError = false;
    disableError = false;
    busy = false;

    get verifyingText() { return this.i18n.tr('totp.verifying'); }
    get verifyEnableText() { return this.i18n.tr('totp.verify-enable'); }
    get disablingText() { return this.i18n.tr('totp.disabling'); }
    get disableText() { return this.i18n.tr('totp.disable'); }

    attaching() {
        this.loadStatus();
    }

    async loadStatus() {
        try {
            const status = await this.totpService.getStatus();
            this.state = status.enabled ? 'enabled' : 'disabled';
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('totp.load-error')});
            this.state = 'disabled';
        }
    }

    async startSetup() {
        try {
            this.busy = true;
            this.setupData = await this.totpService.setup();
            this.verifyCode = '';
            this.verifyError = false;
            this.state = 'setup';
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('totp.setup-error')});
        } finally {
            this.busy = false;
        }
    }

    async verifyAndEnable() {
        if (!this.verifyCode.trim()) return;
        try {
            this.busy = true;
            this.verifyError = false;
            const response = await this.totpService.enable(this.verifyCode.trim());
            this.recoveryCodes = response.recoveryCodes;
            this.state = 'recovery-codes';
            this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr('totp.enabled-alert')});
        } catch {
            this.verifyError = true;
        } finally {
            this.busy = false;
        }
    }

    finishSetup() {
        this.setupData = null;
        this.recoveryCodes = [];
        this.state = 'enabled';
    }

    startDisable() {
        this.disableCode = '';
        this.disableError = false;
        this.state = 'disabling';
    }

    cancelDisable() {
        this.state = 'enabled';
    }

    async confirmDisable() {
        if (!this.disableCode.trim()) return;
        try {
            this.busy = true;
            this.disableError = false;
            await this.totpService.disable(this.disableCode.trim());
            this.state = 'disabled';
            this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr('totp.disabled-alert')});
        } catch {
            this.disableError = true;
        } finally {
            this.busy = false;
        }
    }

    cancelSetup() {
        this.setupData = null;
        this.state = 'disabled';
    }

    async copyRecoveryCodes() {
        try {
            await navigator.clipboard.writeText(this.recoveryCodes.join('\n'));
            this.ea.publish('alert', {alertType: AlertType.Success, text: this.i18n.tr('totp.recovery-copied')});
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: this.i18n.tr('totp.copy-error')});
        }
    }

    downloadRecoveryCodes() {
        const text = "Let's Peppol - Recovery Codes\n" +
            "==============================\n\n" +
            this.recoveryCodes.join('\n') +
            "\n\nStore these codes in a safe place.\nEach code can only be used once.\n";
        const blob = new Blob([text], {type: 'text/plain'});
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'letspeppol-recovery-codes.txt';
        a.click();
        URL.revokeObjectURL(url);
    }
}

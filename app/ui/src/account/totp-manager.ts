import {resolve} from "@aurelia/kernel";
import {IEventAggregator} from "aurelia";
import {TotpService, TotpSetupResponse} from "../services/kyc/totp-service";
import {AlertType} from "../components/alert/alert";

type TotpState = 'loading' | 'disabled' | 'setup' | 'verify' | 'recovery-codes' | 'enabled' | 'disabling';

export class TotpManager {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly totpService = resolve(TotpService);

    state: TotpState = 'loading';
    setupData: TotpSetupResponse | null = null;
    recoveryCodes: string[] = [];
    verifyCode = '';
    disableCode = '';
    verifyError = false;
    disableError = false;
    busy = false;

    attaching() {
        this.loadStatus();
    }

    async loadStatus() {
        try {
            const status = await this.totpService.getStatus();
            this.state = status.enabled ? 'enabled' : 'disabled';
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to load 2FA status"});
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
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to start 2FA setup"});
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
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Two-factor authentication enabled"});
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
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Two-factor authentication disabled"});
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
            this.ea.publish('alert', {alertType: AlertType.Success, text: "Recovery codes copied"});
        } catch {
            this.ea.publish('alert', {alertType: AlertType.Danger, text: "Failed to copy"});
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

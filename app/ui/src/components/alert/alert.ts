import {IDisposable, IEventAggregator} from "aurelia";
import {resolve} from "@aurelia/kernel";

export enum AlertType {
    Success= 'Success',
    Info = 'Info',
    Warning = 'Warning',
    Danger = 'Danger',
    Neutral = 'Neutral'
}

export class AlertMessage {
    public alertType: AlertType;
    public text: string;
}

export class Alert {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    private alertMessageSubscription: IDisposable;
    private alertType = AlertType.Success;
    private showElement = false;
    private text = "";
    private timeout?: number | undefined;

    bound() {
        this.alertMessageSubscription = this.ea.subscribe('alert', (message: AlertMessage) => this.showAlert(message.alertType, message.text));
    }

    unbinding() {
        if (this.alertMessageSubscription) this.alertMessageSubscription.dispose();
    }

    showAlert(alertType: AlertType, text: string) {
        if (this.timeout) {
            clearTimeout(this.timeout);
        }
        this.alertType = alertType;
        this.text = text;
        let element = document.getElementById('alert');
        element.classList.remove('alert--animation');
        void element.offsetWidth;
        element.classList.add('alert--animation');
        this.showElement = true;
        // this.timeout = window.setTimeout(() => this.showElement = false, 3000);
    }


    closeAlert() {
        this.showElement = false;
    }

}

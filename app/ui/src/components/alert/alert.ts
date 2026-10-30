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

export interface Toast {
    id: number;
    alertType: AlertType;
    text: string;
}

const MAX_TOASTS = 3;
const SHORT_DURATION_MS = 5000;
const LONG_DURATION_MS = 10000;

export class Alert {
    readonly ea: IEventAggregator = resolve(IEventAggregator);
    private alertMessageSubscription?: IDisposable;
    private readonly timers = new Map<number, number>();
    private nextId = 1;
    toasts: Toast[] = [];

    bound() {
        this.alertMessageSubscription = this.ea.subscribe('alert', (message: AlertMessage) => this.showAlert(message.alertType, message.text));
    }

    unbinding() {
        this.alertMessageSubscription?.dispose();
        for (const timer of this.timers.values()) {
            clearTimeout(timer);
        }
        this.timers.clear();
    }

    showAlert(alertType: AlertType, text: string) {
        for (const toast of this.toasts.filter(existing => existing.alertType === alertType && existing.text === text)) {
            this.dismiss(toast);
        }
        while (this.toasts.length >= MAX_TOASTS) {
            this.dismiss(this.toasts[0]);
        }
        const toast: Toast = {id: this.nextId++, alertType, text};
        this.toasts = [...this.toasts, toast];
        this.resume(toast);
    }

    dismiss(toast: Toast) {
        this.pause(toast);
        this.toasts = this.toasts.filter(existing => existing.id !== toast.id);
    }

    pause(toast: Toast) {
        clearTimeout(this.timers.get(toast.id));
        this.timers.delete(toast.id);
    }

    resume(toast: Toast) {
        this.pause(toast);
        const duration = this.isUrgent(toast) ? LONG_DURATION_MS : SHORT_DURATION_MS;
        this.timers.set(toast.id, window.setTimeout(() => this.dismiss(toast), duration));
    }

    isUrgent(toast: Toast) {
        return toast.alertType === AlertType.Danger || toast.alertType === AlertType.Warning;
    }
}

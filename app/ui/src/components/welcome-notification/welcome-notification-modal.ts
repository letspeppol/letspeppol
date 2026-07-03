import {IDisposable, IEventAggregator} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {I18N} from "@aurelia/i18n";
import {
    CompanyGroup,
    NotificationGroup,
    SEEN_NOTIFICATION_KEY,
    WelcomeNotificationDto,
    WelcomeNotificationService,
    WelcomeNotificationsResponse
} from "../../services/app/welcome-notification-service";
import {onModalEnter} from "../util/modal-keyboard";
import {LoginService} from "../../services/app/login-service";

const SHOW_WELCOME_NOTIFICATIONS = "showWelcomeNotifications";
type SupportedLocale = "en" | "nl" | "fr" | "de";

export class WelcomeNotificationModal {
    private readonly ea: IEventAggregator = resolve(IEventAggregator);
    private readonly i18n = resolve(I18N);
    private readonly welcomeNotificationService = resolve(WelcomeNotificationService);
    private readonly loginService = resolve(LoginService);
    private showSubscription?: IDisposable;
    private currentResponse?: WelcomeNotificationsResponse;
    private seenIds = new Set<number>();
    private pendingSeenIds = new Set<number>();

    open = false;
    loading = false;
    notifications: WelcomeNotificationDto[] = [];
    index = 0;

    bound() {
        this.showSubscription = this.ea.subscribe(SHOW_WELCOME_NOTIFICATIONS, () => this.showWelcomeNotifications());
        if (this.shouldResumeAfterRefresh()) {
            this.showWelcomeNotifications();
        }
    }

    unbinding() {
        this.showSubscription?.dispose();
    }

    get current() {
        return this.notifications[this.index];
    }

    get currentHtmlCode() {
        if (!this.current) {
            return "";
        }
        return this.htmlForLocale(this.current, this.selectedLocale());
    }

    get hasNext() {
        return this.index < this.notifications.length - 1;
    }

    async showWelcomeNotifications() {
        if (this.loading) {
            return;
        }
        this.loading = true;
        try {
            const response = await this.welcomeNotificationService.getWelcomeNotifications();
            this.currentResponse = response;
            this.seenIds = this.loadSeenIds(response);
            this.pendingSeenIds = new Set<number>();
            this.notifications = this.filterNotifications(response);
            this.index = 0;
            this.open = this.notifications.length > 0;
        } catch {
            this.close();
        } finally {
            this.loading = false;
        }
    }

    next() {
        if (!this.hasNext) {
            this.complete();
            return;
        }
        this.markCurrentPending();
        this.index += 1;
    }

    close() {
        this.open = false;
        this.notifications = [];
        this.index = 0;
        this.currentResponse = undefined;
        this.pendingSeenIds = new Set<number>();
    }

    complete() {
        this.markCurrentPending();
        this.commitPendingSeenIds();
        localStorage.setItem(SEEN_NOTIFICATION_KEY, "true");
        this.close();
    }

    onKeyDown(event: KeyboardEvent) {
        onModalEnter(event, () => this.hasNext ? this.next() : this.complete());
    }

    private filterNotifications(response: WelcomeNotificationsResponse) {
        return response.notifications.filter(notification =>
            !this.shouldStoreSeenId(response.companyGroup, notification.notificationGroup)
            || !this.seenIds.has(notification.id)
        );
    }

    private markCurrentPending() {
        if (!this.currentResponse || !this.current || !this.shouldStoreSeenId(this.currentResponse.companyGroup, this.current.notificationGroup)) {
            return;
        }
        this.pendingSeenIds.add(this.current.id);
    }

    private commitPendingSeenIds() {
        if (!this.currentResponse || this.pendingSeenIds.size === 0) {
            return;
        }
        for (const id of this.pendingSeenIds) {
            this.seenIds.add(id);
        }
        this.saveSeenIds(this.currentResponse, this.seenIds);
    }

    private loadSeenIds(response: WelcomeNotificationsResponse) {
        try {
            const ids = JSON.parse(localStorage.getItem(this.storageKey(response)) || "[]");
            if (!Array.isArray(ids)) {
                return new Set<number>();
            }
            return new Set(ids.filter(id => Number.isInteger(id)));
        } catch {
            return new Set<number>();
        }
    }

    private saveSeenIds(response: WelcomeNotificationsResponse, ids: Set<number>) {
        localStorage.setItem(this.storageKey(response), JSON.stringify([...ids]));
    }

    private storageKey(response: WelcomeNotificationsResponse) {
        return `welcomeNotifications.seen.${response.peppolId}`;
    }

    private shouldStoreSeenId(companyGroup: CompanyGroup, notificationGroup: NotificationGroup) {
        return companyGroup === "SPONSOR" || companyGroup === "ONCE" || notificationGroup === "ONCE";
    }

    private shouldResumeAfterRefresh() {
        return this.loginService.authenticated
            && localStorage.getItem(SEEN_NOTIFICATION_KEY) !== "true"
            && !window.location.pathname.endsWith("/login");
    }

    private selectedLocale(): SupportedLocale {
        const locale = (localStorage.getItem("locale") || this.i18n.getLocale() || "en")
            .slice(0, 2)
            .toLowerCase();
        return this.isSupportedLocale(locale) ? locale : "en";
    }

    private isSupportedLocale(locale: string): locale is SupportedLocale {
        return locale === "en" || locale === "nl" || locale === "fr" || locale === "de";
    }

    private htmlForLocale(notification: WelcomeNotificationDto, locale: SupportedLocale) {
        switch (locale) {
            case "nl":
                return notification.htmlCodeNl || notification.htmlCodeEn;
            case "fr":
                return notification.htmlCodeFr || notification.htmlCodeEn;
            case "de":
                return notification.htmlCodeDe || notification.htmlCodeEn;
            case "en":
            default:
                return notification.htmlCodeEn || notification.htmlCodeNl || notification.htmlCodeFr || notification.htmlCodeDe || "";
        }
    }
}

export {SHOW_WELCOME_NOTIFICATIONS};

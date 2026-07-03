import {resolve} from "@aurelia/kernel";
import {singleton} from "aurelia";
import {AppApi} from "./app-api";

export const SEEN_NOTIFICATION_KEY = "seenNotification";

export type CompanyGroup = "USER" | "SPECIAL" | "SPONSOR" | "EDITOR";
export type NotificationGroup = "USER" | "SPECIAL" | "SPONSOR" | "EDITOR" | "ONCE";

export interface WelcomeNotificationDto {
    id: number,
    htmlCodeEn: string,
    htmlCodeNl: string,
    htmlCodeFr: string,
    htmlCodeDe: string,
    notificationGroup: NotificationGroup
}

export interface WelcomeNotificationsResponse {
    peppolId: string,
    companyGroup: CompanyGroup,
    notifications: WelcomeNotificationDto[]
}

@singleton()
export class WelcomeNotificationService {
    private appApi = resolve(AppApi);

    async getWelcomeNotifications(): Promise<WelcomeNotificationsResponse> {
        return this.appApi.httpClient.get("/sapi/welcome-notifications")
            .then(response => response.json());
    }
}

import {lazy, resolve} from "@aurelia/kernel";
import {IEventAggregator, newInstanceOf, singleton} from "aurelia";
import {IHttpClient} from "@aurelia/fetch-client";
import {I18N} from "@aurelia/i18n";
import {LoginService} from "./login-service";
import {isMissingPermission} from "../util/forbidden-response";
import {AlertType} from "../../components/alert/alert";

@singleton()
export class AppApi {
    public httpClient = resolve(newInstanceOf(IHttpClient));
    private readonly loginService = resolve(lazy(LoginService));
    private readonly ea = resolve(IEventAggregator);
    private readonly i18n = resolve(I18N);

    constructor() {
        const baseUrl = import.meta.env.VITE_APP_BASE_URL || '/app';
        this.httpClient.configure(config => config
            .withBaseUrl(baseUrl)
            // No token at construction — LoginService.setAuthHeader() injects the bearer header once
            // a token is obtained (tokens are held in memory, never in localStorage).
            .withDefaults({
                credentials: "include"
            })
            .rejectErrorResponses()
            .withInterceptor({
                responseError: async (error: Response, request?: Request) => {
                    if (error.status === 401) {
                        void this.loginService().recoverFromUnauthorized(request?.headers.get('Authorization') ?? null);
                    } else if (await isMissingPermission(error)) {
                        this.ea.publish('alert', {alertType: AlertType.Warning, text: this.i18n.tr('error.MISSING_PERMISSION')});
                    }
                    throw error;
                }
            })
        );
    }
}

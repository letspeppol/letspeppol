import {resolve} from "@aurelia/kernel";
import {IEventAggregator, newInstanceOf, singleton} from "aurelia";
import {IHttpClient} from "@aurelia/fetch-client";
import {Router} from "@aurelia/router";
import {I18N} from "@aurelia/i18n";
import {rememberCurrentNavigation} from "../app/ownership-route";
import {isMissingPermission} from "../util/forbidden-response";
import {AlertType} from "../../components/alert/alert";

@singleton()
export class KYCApi {
    public httpClient = resolve(newInstanceOf(IHttpClient));
    private readonly router = resolve(Router);
    private readonly ea = resolve(IEventAggregator);
    private readonly i18n = resolve(I18N);

    constructor() {
        const baseUrl = import.meta.env.VITE_KYC_BASE_URL || '/kyc';
        this.httpClient.configure(config => config
            .withBaseUrl(baseUrl)
            // No token at construction — LoginService.setAuthHeader() injects the bearer header once
            // a token is obtained (tokens are held in memory, never in localStorage).
            .withDefaults({})
            .rejectErrorResponses()
            .withInterceptor({
                responseError: async (error: Response) => {
                    if (error.status === 401) {
                        rememberCurrentNavigation();
                        localStorage.removeItem('token');
                        localStorage.removeItem('peppolActive');
                        this.router.load('/login');
                    } else if (await isMissingPermission(error)) {
                        this.ea.publish('alert', {alertType: AlertType.Warning, text: this.i18n.tr('error.MISSING_PERMISSION')});
                    }
                    throw error;
                }
            })
        );
    }
}

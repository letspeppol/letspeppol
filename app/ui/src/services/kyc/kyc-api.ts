import {resolve} from "@aurelia/kernel";
import {newInstanceOf, singleton} from "aurelia";
import {IHttpClient} from "@aurelia/fetch-client";
import {Router} from "@aurelia/router";

@singleton()
export class KYCApi {
    public httpClient = resolve(newInstanceOf(IHttpClient));
    private readonly router = resolve(Router);

    constructor() {
        const baseUrl = import.meta.env.VITE_KYC_BASE_URL || '/kyc';
        this.httpClient.configure(config => config
            .withBaseUrl(baseUrl)
            // No token at construction — LoginService.setAuthHeader() injects the bearer header once
            // a token is obtained (tokens are held in memory, never in localStorage).
            .withDefaults({})
            .rejectErrorResponses()
            .withInterceptor({
                responseError: (error: Response) => {
                    if (error.status === 401) {
                        this.router.load('login');
                    }
                    throw error;
                }
            })
        );
    }
}

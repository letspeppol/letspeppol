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
            .withDefaults({
                headers: localStorage.getItem('token') ? {'Authorization': `Bearer ${localStorage.getItem('token')}`} : undefined,
            })
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
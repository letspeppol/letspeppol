import {newInstanceOf, singleton} from "aurelia";
import {resolve} from "@aurelia/kernel";
import {IHttpClient} from "@aurelia/fetch-client";
import {Router} from "@aurelia/router";

@singleton()
export class PeppolDirApi {
    public httpClient = resolve(newInstanceOf(IHttpClient));
    private readonly router = resolve(Router);

    constructor() {
        const baseUrl = import.meta.env.VITE_PEPPOL_DIR_URL || 'https://test-directory.peppol.eu';
        this.httpClient.configure(config => config
            .withBaseUrl(baseUrl)
            .withDefaults({
                headers: {'Authorization': `Bearer ${localStorage.getItem('token') ?? ''}`}
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
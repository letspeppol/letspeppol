import {resolve} from "@aurelia/kernel";
import {newInstanceOf, singleton} from "aurelia";
import {IHttpClient} from "@aurelia/fetch-client";
import {Router} from "@aurelia/router";

@singleton()
export class AppApi {
    public httpClient = resolve(newInstanceOf(IHttpClient));
    private readonly router = resolve(Router);

    constructor() {
        this.httpClient.configure(config => config
            .withBaseUrl('http://localhost:8082')
            .withDefaults({
                headers: {'Authorization': `Bearer ${localStorage.getItem('token') ?? ''}`}
            })
            .rejectErrorResponses()
            .withInterceptor({
                responseError: (error: Response, request) => {
                    if (error.status === 401) {
                        this.router.load('login');
                    }
                    throw error;
                }
            })
        );
    }
}
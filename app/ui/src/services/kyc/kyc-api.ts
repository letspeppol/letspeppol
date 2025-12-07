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
            .rejectErrorResponses()
            .withInterceptor({
                request: (request) => {
                  const token = localStorage.getItem('token');
                  if (token) {
                      request.headers.set('Authorization', `Bearer ${token}`);
                  }
                  return request;
                },
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
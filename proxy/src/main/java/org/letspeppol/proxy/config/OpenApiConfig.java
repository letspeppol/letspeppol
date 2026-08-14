package org.letspeppol.proxy.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI proxyOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Let’s Peppol Proxy API")
                        .version("v1")
                        .description("""
                                Proxy registry and document-transport endpoints.

                                `/sapi/document/**` accepts KYC JWTs carrying a `uid`: normally a user's
                                Authorization Code + PKCE token forwarded by App, or App's client-credentials
                                token for background synchronization. `/sapi/registry/**` is service-only and
                                requires the `service` scope. `/api/stats/**` is public. Local-control APIs are
                                intentionally omitted from this OpenAPI document.

                                JWTs are verified against KYC JWKS and checked for expiry, configured issuer,
                                and audience `letspeppol-api`. The optional `X-Acting-User-Authorization`
                                header carries an end-user token only when an APP service token performs an
                                operation that still needs user identity; it never replaces the primary bearer
                                token.

                                Error bodies use either `{ "errorCode": "...", "message": "..." }` or
                                `{ "message": "..." }`. Access-point outages can return 503 with Retry-After;
                                duplicate or conflicting operations return 409.
                                """)
                        .contact(new Contact().name("Let’s Peppol")))
                .servers(java.util.List.of(new Server()
                        .url("/proxy")
                        .description("Proxy API through the public reverse proxy")))
                .components(new Components()
                        .addSecuritySchemes("oauth2", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .description("KYC browser Authorization Code flow. PKCE (S256) is mandatory.")
                                .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                                        .authorizationUrl("/kyc/oauth2/authorize")
                                        .tokenUrl("/kyc/oauth2/token")
                                        .scopes(new Scopes().addString("openid", "OpenID Connect sign-in")))))
                        .addSecuritySchemes("serviceAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .description("Trusted backend client-credentials flow for registry and background document traffic.")
                                .flows(new OAuthFlows().clientCredentials(new OAuthFlow()
                                        .tokenUrl("/kyc/oauth2/token")
                                        .scopes(new Scopes().addString("service", "Service-to-service API access")))))
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Manual JWT entry for diagnostics; normal callers obtain it through OAuth2.")));
    }
}

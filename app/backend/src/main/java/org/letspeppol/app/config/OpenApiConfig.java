package org.letspeppol.app.config;

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
    public OpenAPI appOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Let’s Peppol App API")
                        .version("v1")
                        .description("""
                                Application, document, partner, product, company, and statistics endpoints.

                                `/api/**` endpoints are public. `/sapi/**` endpoints require an access token
                                issued by KYC's OAuth 2.0 Authorization Code + PKCE flow. The App validates JWT
                                signature through KYC JWKS, plus expiry, configured issuer, and audience
                                `letspeppol-api`. It forwards the user's token when a downstream KYC or Proxy
                                operation must retain user/company context. Scheduled jobs and sponsor invoice
                                creation instead use the trusted `kyc-service` client-credentials grant.

                                Error bodies use either `{ "errorCode": "..." }` or `{ "message": "..." }`;
                                validation failures include an `errors` field map. Downstream KYC/Proxy failures
                                preserve their meaningful HTTP status where possible.
                                """)
                        .contact(new Contact().name("Let’s Peppol")))
                .servers(java.util.List.of(new Server()
                        .url("/app")
                        .description("App API through the public reverse proxy")))
                .components(new Components()
                        .addSecuritySchemes("oauth2", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .description("KYC browser Authorization Code flow. PKCE (S256) is mandatory.")
                                .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                                        .authorizationUrl("/kyc/oauth2/authorize")
                                        .tokenUrl("/kyc/oauth2/token")
                                        .scopes(new Scopes().addString("openid", "OpenID Connect sign-in")))))
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Manual JWT entry for diagnostics; normal browser clients use OAuth2 + PKCE.")));
    }
}

package org.letspeppol.kyc.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.configuration.SpringDocSecurityOAuth2Customizer;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI kycOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Let’s Peppol KYC API")
                        .version("v1")
                        .description("""
                                KYC, registration, authentication, and identity-verification endpoints.

                                Browser authentication has two layers. `/auth/**` establishes a server-side
                                browser session (password plus optional TOTP/recovery code, or a passkey). The
                                SPA then uses OAuth 2.0 Authorization Code with PKCE at `/oauth2/authorize` and
                                `/oauth2/token`. The public SPA is `letspeppol-ui`; it has no client secret and
                                receives no refresh token. Token renewal and ownership changes use silent
                                re-authorization (`prompt=none`).

                                Path families: `/api/**` is public, `/auth/**` is the cookie/CSRF browser
                                surface, and `/sapi/**` requires an OAuth access token with company context.
                                Local-control APIs are intentionally omitted from this OpenAPI document. KYC
                                also issues `service` scoped client-credentials tokens to trusted backends.
                                Protected APIs validate
                                JWT signature, expiry, issuer (when configured), and audience
                                `letspeppol-api`.

                                Application errors are JSON objects with `errorCode`; validation errors may
                                also contain an `errors` map. Authentication failures can be 401, authorization
                                failures 403, rate limits 429, and unexpected failures 500.
                                """)
                        .contact(new Contact().name("Let’s Peppol")))
                .servers(java.util.List.of(new Server()
                        .url("/kyc")
                        .description("KYC through the public reverse proxy")))
                .components(new Components()
                        .addSecuritySchemes("oauth2", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .description("Browser Authorization Code flow. PKCE (S256) is mandatory.")
                                .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                                        .authorizationUrl("/kyc/oauth2/authorize")
                                        .tokenUrl("/kyc/oauth2/token")
                                        .scopes(new Scopes().addString("openid", "OpenID Connect sign-in")))))
                        .addSecuritySchemes("serviceAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .description("Trusted backend client-credentials flow; never expose its secret to a browser.")
                                .flows(new OAuthFlows().clientCredentials(new OAuthFlow()
                                        .tokenUrl("/kyc/oauth2/token")
                                        .scopes(new Scopes().addString("service", "Service-to-service API access")))))
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Manual JWT entry for diagnostics; normal clients use one of the OAuth2 flows.")));
    }

    @Bean
    public GlobalOpenApiCustomizer oauth2EndpointsWithoutDiscoveryMetadata(
            ApplicationContext applicationContext) {
        SpringDocSecurityOAuth2Customizer oauth2Customizer = new SpringDocSecurityOAuth2Customizer();
        oauth2Customizer.setApplicationContext(applicationContext);
        return openApi -> {
            oauth2Customizer.customise(openApi);
            if (openApi.getPaths() != null) {
                openApi.getPaths().keySet().removeIf(path -> path.contains("/.well-known/"));
                addLoginOperation(openApi);
                describeGeneratedSecurityOperations(openApi);
            }
        };
    }

    private static void addLoginOperation(OpenAPI openApi) {
        ObjectSchema credentials = new ObjectSchema();
        credentials.addProperty("username", new StringSchema());
        credentials.addProperty("password", new StringSchema().format("password"));
        credentials.addProperty("_csrf",
                new StringSchema().description("CSRF token obtained from GET /auth/session."));
        RequestBody requestBody = new RequestBody()
                .required(true)
                .content(new Content().addMediaType(
                        org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                        new MediaType().schema(credentials)));
        ApiResponses responses = new ApiResponses()
                .addApiResponse("200", new ApiResponse().description("Browser session established."))
                .addApiResponse("401", new ApiResponse().description("Credentials or second-factor state rejected."));
        Operation operation = new Operation()
                .addTagsItem("login-endpoint")
                .requestBody(requestBody)
                .responses(responses);
        openApi.getPaths().addPathItem("/login", new PathItem().post(operation));
    }

    private static void describeGeneratedSecurityOperations(OpenAPI openApi) {
        openApi.getPaths().forEach((path, pathItem) -> pathItem.readOperations().forEach(operation -> {
            if (operation.getTags() == null
                    || operation.getTags().stream().noneMatch(tag ->
                    tag.equals("authorization-server-endpoints") || tag.equals("login-endpoint"))) {
                return;
            }
            operation.setSummary(generatedEndpointSummary(path));
            operation.setDescription(generatedEndpointDescription(path));
        }));
    }

    private static String generatedEndpointSummary(String path) {
        return switch (path) {
            case "/login" -> "Authenticate the browser session";
            case "/oauth2/authorize" -> "Start OAuth2 authorization";
            case "/oauth2/token" -> "Issue OAuth2 tokens";
            case "/oauth2/jwks" -> "Read JWT verification keys";
            case "/oauth2/introspect" -> "Inspect an OAuth2 token";
            case "/oauth2/revoke" -> "Revoke an OAuth2 token";
            case "/userinfo" -> "Read OpenID Connect user information";
            default -> "OAuth2 authorization-server operation";
        };
    }

    private static String generatedEndpointDescription(String path) {
        return switch (path) {
            case "/login" -> "Accepts the user's credentials and establishes the server-side browser "
                    + "session required before Authorization Code with PKCE. Browser submissions require CSRF protection.";
            case "/oauth2/authorize" -> "Validates the OAuth2 authorization request and PKCE challenge using the "
                    + "authenticated browser session, then redirects the client with an authorization code and state.";
            case "/oauth2/token" -> "Exchanges an authorization code plus PKCE verifier for user tokens, or accepts "
                    + "client credentials from a trusted backend to issue a service-scoped access token.";
            case "/oauth2/jwks" -> "Returns the public JSON Web Keys used by App and Proxy to verify KYC-issued JWT signatures.";
            case "/oauth2/introspect" -> "Returns metadata describing a token to an authenticated OAuth2 client.";
            case "/oauth2/revoke" -> "Invalidates a token submitted by an authenticated OAuth2 client.";
            case "/userinfo" -> "Returns OpenID Connect claims for the subject represented by a valid access token.";
            default -> "Protocol operation supplied by the KYC OAuth2 authorization server.";
        };
    }
}

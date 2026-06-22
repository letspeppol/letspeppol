package org.letspeppol.kyc.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.repository.AccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Configuration
public class SecurityConfig {

    public static final String ROLE_KYC_USER = "kyc_user";

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${service.client.secret:#{null}}")
    private String serviceClientSecret;

    @Value("${oauth2.ui.redirect-uri:https://localpeppol.org:3001/callback}")
    private String redirectUri;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource) throws Exception {

        OAuth2AuthorizationServerConfigurer configurer = new OAuth2AuthorizationServerConfigurer();

        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setMatchingRequestParameterName(null);

        http
                .securityMatcher(configurer.getEndpointsMatcher())
                .requestCache(rc -> rc.requestCache(requestCache))
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .csrf(csrf -> csrf.ignoringRequestMatchers(configurer.getEndpointsMatcher()))
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .with(configurer, c -> c.oidc(Customizer.withDefaults()))
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .formLogin(form -> form.loginPage("/login").successHandler(totpAuthenticationSuccessHandler()).permitAll());
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder, CorsConfigurationSource corsConfigurationSource) throws Exception {
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setMatchingRequestParameterName(null);

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // CSRF guards only the cookie/session browser surface (the /login form, which carries a token).
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/sapi/**", "/actuator/**"))
                .requestCache(rc -> rc.requestCache(requestCache))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/totp-verify", "/error", "/css/**", "/images/**", "/js/**", "/favicon.ico").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/**").permitAll()
                        .requestMatchers("/sapi/**").hasAuthority(ROLE_KYC_USER)
                        .anyRequest().denyAll()
                )
                .formLogin(form -> form.loginPage("/login").successHandler(totpAuthenticationSuccessHandler()).permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())
                ));
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            if (jwt.hasClaim("peppolId")) {
                authorities.add(new SimpleGrantedAuthority(ROLE_KYC_USER));
            }
            return authorities;
        });
        return converter;
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${jwt.public-key}") RSAPublicKey publicKey,
            @Value("${oauth2.audience:letspeppol-api}") String audience,
            @Value("${spring.security.oauth2.authorizationserver.issuer:}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        decoder.setJwtValidator(JwtValidationSupport.build(audience, issuer));
        return decoder;
    }

    @Bean
    public OAuth2AuthorizationService authorizationService(
            org.springframework.jdbc.core.JdbcOperations jdbcOperations,
            RegisteredClientRepository registeredClientRepository) {
        JdbcOAuth2AuthorizationService service = new JdbcOAuth2AuthorizationService(jdbcOperations, registeredClientRepository);
        JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper rowMapper =
                new JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper(registeredClientRepository);
        com.fasterxml.jackson.databind.ObjectMapper objectMapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        objectMapper.registerModules(org.springframework.security.jackson2.SecurityJackson2Modules.getModules(getClass().getClassLoader()));
        objectMapper.registerModule(new org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module());
        objectMapper.registerModule(new AccountUserDetailsJacksonModule());
        rowMapper.setObjectMapper(objectMapper);
        service.setAuthorizationRowMapper(rowMapper);
        return service;
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public OAuth2TokenGenerator<?> tokenGenerator(JwtEncoder jwtEncoder, OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer) {
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        jwtGenerator.setJwtCustomizer(tokenCustomizer);
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, new OAuth2AccessTokenGenerator(), new OAuth2RefreshTokenGenerator());
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(PasswordEncoder passwordEncoder) {
        RegisteredClient uiClient = RegisteredClient.withId("ui")
                .clientId("letspeppol-ui")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                // No REFRESH_TOKEN grant: the SPA holds tokens in memory and renews via silent
                // re-authorization, so no refresh token is issued to the browser.
                .redirectUri(redirectUri)
                .postLogoutRedirectUri(redirectUri.replace("/callback", "/login"))
                .scope(OidcScopes.OPENID)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .build())
                .build();

        if (serviceClientSecret == null || serviceClientSecret.isBlank()) {
            throw new IllegalStateException("SERVICE_CLIENT_SECRET must be set");
        }

        RegisteredClient serviceClient = RegisteredClient.withId("service")
                .clientId("kyc-service")
                // No "{bcrypt}" prefix: the PasswordEncoder bean is a plain BCryptPasswordEncoder
                // (kept that way for raw-bcrypt user password hashes), not a DelegatingPasswordEncoder,
                // so a "{bcrypt}$2a$..." value would never match and client auth would fail.
                .clientSecret(passwordEncoder.encode(serviceClientSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("service")
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .build())
                .build();

        return new InMemoryRegisteredClientRepository(uiClient, serviceClient);
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(
            @Value("${jwt.private-key}") RSAPrivateKey privateKey,
            @Value("${jwt.public-key}") RSAPublicKey publicKey) {
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID("kyc-key-1")
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(
            @Value("${spring.security.oauth2.authorizationserver.issuer:}") String issuer) {
        AuthorizationServerSettings.Builder builder = AuthorizationServerSettings.builder();
        if (issuer != null && !issuer.isBlank()) {
            builder.issuer(issuer);
        }
        return builder.build();
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer(
            AccountRepository accountRepository,
            @Value("${oauth2.audience:letspeppol-api}") String audience,
            @Value("${oauth2.app-client.account-external-id:b095630d-1bf3-4250-bf9e-2d49e6ce505b}") String appAccountExternalId) {
        return context -> {
            JwtClaimsSet.Builder claims = context.getClaims();

            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                claims.audience(new ArrayList<>(List.of(audience)));
            }

            if (context.getPrincipal().getPrincipal() instanceof AccountUserDetails userDetails) {
                claims.claim("peppolId", userDetails.getPeppolId());
                claims.claim("peppolActive", userDetails.isPeppolActive());
                claims.claim("uid", userDetails.getUid().toString());
                claims.claim("accountType", userDetails.getAccountType().name());
            } else if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())
                    && "kyc-service".equals(context.getRegisteredClient().getClientId())) {
                // Service-to-service token for the App backend's scheduled document sync:
                // act as the seeded APP account so the proxy can resolve app-linked documents.
                Account app = accountRepository
                        .findByExternalId(UUID.fromString(appAccountExternalId))
                        .orElseThrow(() -> new IllegalStateException("App service account not found: " + appAccountExternalId));
                claims.claim("uid", app.getExternalId().toString());
                claims.claim("accountType", app.getType().name());
                if (app.getCompany() != null && app.getCompany().getPeppolId() != null) {
                    claims.claim("peppolId", app.getCompany().getPeppolId());
                    claims.claim("peppolActive", app.getCompany().isPeppolActive());
                }
            }
        };
    }

    @Bean
    public TotpAuthenticationSuccessHandler totpAuthenticationSuccessHandler() {
        return new TotpAuthenticationSuccessHandler();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        String[] origins = allowedOrigins.split("[,;\\s]+");
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Location", "Content-Disposition", "Registration-Status", "Registration-Provider"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

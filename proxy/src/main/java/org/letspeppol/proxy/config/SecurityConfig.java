package org.letspeppol.proxy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Configuration
public class SecurityConfig {

    public static final String PEPPOL_ID = "peppolId";
    public static final String PEPPOL_ACTIVE = "peppolActive";
    public static final String UID = "uid"; //Needed for multiple accounts to a joined Peppol ID
    public static final String ROLE_SERVICE = "service";
    public static final String ACCOUNT_TYPE = "accountType";
    public static final String ROLE_KYC_USER = "kyc_user";

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> {})
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/scrada/**").permitAll()
                        .requestMatchers("/api/e-invoice/**").permitAll()
                        .requestMatchers("/api/monitor/**").permitAll()//.hasAuthority(ROLE_SERVICE) --> /sapi/monitor/ ???
                        .requestMatchers("/api/stats/**").permitAll()
                        .requestMatchers("/sapi/registry/**").hasAuthority(ROLE_SERVICE)
                        .requestMatchers("/sapi/**").hasAuthority(ROLE_KYC_USER)
                        .anyRequest().denyAll()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                        jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())
                ));
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            List<String> scopes = jwt.getClaimAsStringList("scope");
            if (scopes != null && scopes.contains("service")) {
                authorities.add(new SimpleGrantedAuthority(ROLE_SERVICE));
            }
            // Both end-user tokens and the APP service token carry a uid; APP accounts have no
            // peppolId, so uid (not peppolId) is the correct discriminator for ROLE_KYC_USER here.
            if (jwt.hasClaim(UID)) {
                authorities.add(new SimpleGrantedAuthority(ROLE_KYC_USER));
            }
            return authorities;
        });
        return converter;
    }

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${oauth2.audience:letspeppol-api}") String audience,
            @Value("${oauth2.issuer:}") String issuer,
            Environment environment) {
        requireTrustworthyJwkSetUri(jwkSetUri, environment);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidationSupport.build(audience, issuer));
        return decoder;
    }

    // The JWKS endpoint is the JWT signature trust anchor, so in a deployed profile refuse to start
    // on the loopback default (it means KYC_JWKS_URI was left unset). Internal HTTP to KYC is fine.
    private static void requireTrustworthyJwkSetUri(String jwkSetUri, Environment environment) {
        if (!environment.matchesProfiles("postgres")) {
            return;
        }
        String host = jwkSetUri == null ? null : URI.create(jwkSetUri).getHost();
        boolean loopback = host == null
                || host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1");
        if (loopback) {
            throw new IllegalStateException(
                    "KYC_JWKS_URI must point at the KYC server in deployed environments; refusing to "
                            + "trust the loopback default '" + jwkSetUri + "' as the JWT signature source");
        }
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        String[] origins = allowedOrigins.split("[,;\\s]+");
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Location"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

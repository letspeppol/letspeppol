package org.letspeppol.app.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.app.exception.AppErrorCodes;
import org.letspeppol.app.exception.AppException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Slf4j
@Service
public class JwtService {
    public static final String ROLE_SERVICE = "service";

    private final Key key;
    private final WebClient kycWebClient;
    private final String appExternalId;
    private final String appPassword;

    public JwtService(
            @Value("${jwt.secret}") String secretKey,
            @Qualifier("kycWebClient") WebClient kycWebClient,
            @Value("${kyc.auth.app.external-id}") String appExternalId,
            @Value("${kyc.auth.app.password}") String appPassword
    ) {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        this.kycWebClient = kycWebClient;
        this.appExternalId = appExternalId;
        this.appPassword = appPassword;
    }

    public String generateInternalToken() {
        long expirationTime = 1000 * 60 * 60 * 24; // 1 day
        return Jwts.builder()
                .setIssuer("proxy")
                .claim("role", ROLE_SERVICE)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String getAppTokenFromKyc() {
        try {
            return kycWebClient.post()
                    .uri("/api/jwt/auth")
                    .headers(headers -> headers.setBasicAuth(appExternalId, appPassword))
                    .retrieve()
                    .bodyToMono(String.class)
                    .blockOptional()
                    .orElseThrow(() -> new IllegalStateException("Account was not know at KYC"));
        } catch (Exception e) {
            log.error("Call to KYC /api/jwt/auth failed", e);
            throw new AppException(AppErrorCodes.KYC_REST_ERROR);
        }
    }
}


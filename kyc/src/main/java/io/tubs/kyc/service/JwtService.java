package io.tubs.kyc.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.tubs.kyc.exception.KycErrorCodes;
import io.tubs.kyc.exception.KycException;
import io.tubs.kyc.service.jwt.JwtInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Service
public class JwtService {

    public static final String PEPPOL_ID = "peppolId";
    public static final String UID = "uid";
    public static final String ROLE_SERVICE = "service";
    private final Key key;

    public JwtService(@Value("${jwt.secret}") String secretKey) {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    // External
    public String generateToken(String peppolId, UUID uid) {
        long expirationTime = 1000 * 60 * 60; // 1 hour
        return Jwts.builder()
                .setIssuer("proxy")
                .claim(PEPPOL_ID, peppolId)
                .claim(UID, uid)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public JwtInfo validateAndGetInfo(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new KycException(KycErrorCodes.AUTHENTCATION_FAILED);
        }
        String token = authHeader.substring("Bearer ".length()).trim();
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return new JwtInfo(
                token,
                claims.get(PEPPOL_ID, String.class),
                claims.get(PEPPOL_ID, String.class).split(":")[1],
                claims.get(UID, String.class)
        );
    }

    // Internal

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

    public boolean validateInternalToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return claims.get(ROLE_SERVICE, boolean.class);
        } catch (Exception e) {
            log.error("Error validating token", e);
            return false;
        }
    }
}


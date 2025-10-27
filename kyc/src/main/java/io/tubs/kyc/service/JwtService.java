package io.tubs.kyc.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Slf4j
@Service
public class JwtService {

    public static final String PEPPOL_ID = "peppolId";
    public static final String ROLE_SERVICE = "service";
    private final Key key;

    public JwtService(@Value("${jwt.secret}") String secretKey) {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    // External
    public String generateToken(String peppolId) {
        long expirationTime = 1000 * 60 * 60; // 1 hour
        return Jwts.builder()
                .setIssuer("proxy")
                .claim(PEPPOL_ID, peppolId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationTime))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String validateToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return claims.get(PEPPOL_ID, String.class);
        } catch (Exception e) {
            log.error("Error validating token", e);
            return null;
        }
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


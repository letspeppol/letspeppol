package org.letspeppol.kyc.service;


import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class JwtServiceTest {

    @Autowired
    private JwtService jwtService;

    @Test
    void testGenerateAndValidateToken() {
        String peppolId = "0208:1023290711";

        String token = jwtService.generateToken(peppolId, UUID.randomUUID());
        assertNotNull(token, "Generated token should not be null");

        String extractedPeppolId = jwtService.validateToken(token);
        assertEquals(peppolId, extractedPeppolId, "Extracted peppolId should match original");
    }

    @Test
    void testInvalidToken() {
        String invalidToken = "this.is.not.a.valid.jwt";

        String result = jwtService.validateToken(invalidToken);
        assertNull(result, "Invalid token should return null");
    }

    @Test
    void testExpiredToken() throws InterruptedException {
        String peppolId = "expired:case";

        String token = jwtService.generateToken(peppolId, UUID.randomUUID());

        // Since default expiry is 1h, token should still be valid now
        String result = jwtService.validateToken(token);
        assertNotNull(result, "Token should still be valid (1h expiry)");
        assertEquals(peppolId, result);
    }
}

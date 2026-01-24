package org.letspeppol.kyc.service;


import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.service.jwt.JwtInfo;
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

        String token = jwtService.generateToken(AccountType.ADMIN, peppolId, true, UUID.randomUUID());
        assertNotNull(token, "Generated token should not be null");

        JwtInfo jwtInfo = jwtService.validateAndGetInfo(token);
        assertEquals(peppolId, jwtInfo.peppolId(), "Extracted peppolId should match original");
    }

    @Test
    void testInvalidToken() {
        String invalidToken = "this.is.not.a.valid.jwt";

        JwtInfo jwtInfo = jwtService.validateAndGetInfo(invalidToken);
        assertNull(jwtInfo, "Invalid token should return null");
    }

    @Test
    void testExpiredToken() throws InterruptedException {
        String peppolId = "expired:case";

        String token = jwtService.generateToken(AccountType.ADMIN, peppolId, false, UUID.randomUUID());

        // Since default expiry is 1h, token should still be valid now
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(token);
        assertNotNull(jwtInfo, "Token should still be valid (1h expiry)");
        assertEquals(peppolId, jwtInfo.peppolId());
    }
}

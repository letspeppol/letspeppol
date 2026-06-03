package org.letspeppol.proxy.controller;

import org.junit.jupiter.api.Test;
import org.letspeppol.proxy.config.SecurityConfig;
import org.letspeppol.proxy.dto.UblDocumentDto;
import org.letspeppol.proxy.model.AccountType;
import org.letspeppol.proxy.model.DocumentDirection;
import org.letspeppol.proxy.model.DocumentType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppControllerTest {

    @Test
    void validateSenderAccountUsesAppPeppolIdAsSender() {
        UUID appUid = UUID.randomUUID();
        String sponsorPeppolId = "0208:1029545627";
        String customerPeppolId = "0208:0123456789";
        JwtDecoder jwtDecoder = mock(JwtDecoder.class);
        AppController controller = new AppController(
                null,
                null,
                null,
                null,
                null,
                jwtDecoder
        );
        Jwt appJwt = jwt(AccountType.APP, sponsorPeppolId, appUid);
        Jwt actingUserJwt = jwt(AccountType.USER, customerPeppolId, UUID.randomUUID());
        when(jwtDecoder.decode("acting-user-token")).thenReturn(actingUserJwt);

        Object senderValidation = ReflectionTestUtils.invokeMethod(
                controller,
                "validateSenderAccount",
                appJwt,
                document(sponsorPeppolId, customerPeppolId),
                "Bearer acting-user-token"
        );

        assertThat(ReflectionTestUtils.getField(senderValidation, "senderPeppolId")).isEqualTo(sponsorPeppolId);
        assertThat(ReflectionTestUtils.getField(senderValidation, "actingUserPeppolId")).isEqualTo(customerPeppolId);
    }

    private UblDocumentDto document(String ownerPeppolId, String partnerPeppolId) {
        return new UblDocumentDto(
                UUID.randomUUID(),
                DocumentDirection.OUTGOING,
                DocumentType.INVOICE,
                ownerPeppolId,
                partnerPeppolId,
                Instant.now(),
                null,
                null,
                null,
                "<Invoice/>"
        );
    }

    private Jwt jwt(AccountType accountType, String peppolId, UUID uid) {
        return Jwt.withTokenValue(uid.toString())
                .header("alg", "none")
                .claim(SecurityConfig.ACCOUNT_TYPE, accountType.name())
                .claim(SecurityConfig.PEPPOL_ID, peppolId)
                .claim(SecurityConfig.UID, uid.toString())
                .build();
    }
}

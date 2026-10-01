package org.letspeppol.kyc.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unknownPathIsReportedAsNotFound() {
        var response = handler.handleNoResourceFound(
                new NoResourceFoundException(HttpMethod.POST, "/kyc/api/jwt/auth", "api/jwt/auth"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isEqualTo(Map.of("errorCode", KycErrorCodes.NOT_FOUND));
    }
}

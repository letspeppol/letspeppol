package org.letspeppol.proxy.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void missingPermissionIsForbiddenWithErrorCode() {
        ResponseEntity<Map<String, Object>> response = handler.handleAccessDenied(new AccessDeniedException("Access Denied"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).containsEntry("errorCode", "MISSING_PERMISSION");
    }

    @Test
    void proxySecurityExceptionIsForbidden() {
        assertThat(handler.handleSecurityException(new SecurityException("Not a user")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void securityHandlerIsBoundToTheProxyException() {
        Method securityHandler = Arrays.stream(GlobalExceptionHandler.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("handleSecurityException"))
                .findFirst()
                .orElseThrow();

        assertThat(securityHandler.getAnnotation(ExceptionHandler.class).value())
                .containsExactly(org.letspeppol.proxy.exception.SecurityException.class);
    }
}

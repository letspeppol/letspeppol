package org.letspeppol.kyc.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.letspeppol.kyc.dto.AuthErrorResponse;
import org.letspeppol.kyc.dto.AuthStatusResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Locale;

public final class BrowserAuthenticationSupport {

    public static final String STATUS_ANONYMOUS = "anonymous";
    public static final String STATUS_TOTP_REQUIRED = "totp_required";
    public static final String STATUS_AUTHENTICATED = "authenticated";

    private BrowserAuthenticationSupport() {}

    public static boolean requestsJson(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        if (accept == null || accept.isBlank()) {
            return false;
        }
        try {
            return MediaType.parseMediaTypes(accept).stream()
                    .anyMatch(mediaType -> !mediaType.isWildcardType()
                            && !mediaType.isWildcardSubtype()
                            && mediaType.getQualityValue() > 0
                            && "application".equalsIgnoreCase(mediaType.getType())
                            && ("json".equalsIgnoreCase(mediaType.getSubtype())
                            || mediaType.getSubtype().toLowerCase(Locale.ROOT).endsWith("+json")));
        } catch (InvalidMediaTypeException _) {
            return false;
        }
    }

    public static void clearSavedRequest(HttpServletRequest request, HttpServletResponse response) {
        new HttpSessionRequestCache().removeRequest(request, response);
    }

    public static void preventCaching(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
    }

    public static void writeStatus(ObjectMapper objectMapper, HttpServletResponse response,
                                   HttpStatus status, String authenticationStatus) throws IOException {
        writeJson(objectMapper, response, status, new AuthStatusResponse(authenticationStatus));
    }

    public static void writeError(ObjectMapper objectMapper, HttpServletResponse response,
                                  HttpStatus status, String errorCode) throws IOException {
        writeJson(objectMapper, response, status, new AuthErrorResponse(errorCode));
    }

    private static void writeJson(ObjectMapper objectMapper, HttpServletResponse response,
                                  HttpStatus status, Object body) throws IOException {
        preventCaching(response);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}

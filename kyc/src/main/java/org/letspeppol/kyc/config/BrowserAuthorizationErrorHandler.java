package org.letspeppol.kyc.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.http.converter.OAuth2ErrorHttpMessageConverter;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class BrowserAuthorizationErrorHandler implements AuthenticationFailureHandler {

    static final String OWNERSHIP_UNAVAILABLE_REASON = "ownership_unavailable";

    private final String uiLoginUrl;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
    private final HttpMessageConverter<OAuth2Error> errorHttpResponseConverter = new OAuth2ErrorHttpMessageConverter();

    public BrowserAuthorizationErrorHandler(String uiLoginUrl) {
        this.uiLoginUrl = uiLoginUrl;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {

        OAuth2Error error = exception instanceof OAuth2AuthenticationException oauth2Exception
                ? oauth2Exception.getError()
                : new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR);
        OAuth2AuthorizationCodeRequestAuthenticationToken authorizationRequest =
                exception instanceof OAuth2AuthorizationCodeRequestAuthenticationException codeRequestException
                        ? codeRequestException.getAuthorizationCodeRequestAuthentication()
                        : null;

        if (authorizationRequest != null && StringUtils.hasText(authorizationRequest.getRedirectUri())) {
            redirectStrategy.sendRedirect(request, response, errorRedirectUri(error, authorizationRequest));
            return;
        }
        if (isBrowserNavigation(request)) {
            redirectStrategy.sendRedirect(request, response, UriComponentsBuilder.fromUriString(uiLoginUrl)
                    .queryParam(OAuth2ParameterNames.ERROR, reasonFor(error))
                    .toUriString());
            return;
        }

        ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(response);
        httpResponse.setStatusCode(HttpStatus.BAD_REQUEST);
        errorHttpResponseConverter.write(
                new OAuth2Error(reasonFor(error), error.getDescription(), error.getUri()), null, httpResponse);
    }

    private static String reasonFor(OAuth2Error error) {
        return ActingOwnershipAuthorizationRequestConverter.isOwnershipUnavailable(error)
                ? OWNERSHIP_UNAVAILABLE_REASON
                : error.getErrorCode();
    }

    private static boolean isBrowserNavigation(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return accept != null && accept.contains(MediaType.TEXT_HTML_VALUE);
    }

    private static String errorRedirectUri(
            OAuth2Error error,
            OAuth2AuthorizationCodeRequestAuthenticationToken authorizationRequest) {

        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromUriString(authorizationRequest.getRedirectUri())
                .queryParam(OAuth2ParameterNames.ERROR, error.getErrorCode());
        if (StringUtils.hasText(error.getDescription())) {
            uriBuilder.queryParam(OAuth2ParameterNames.ERROR_DESCRIPTION, encode(error.getDescription()));
        }
        if (StringUtils.hasText(error.getUri())) {
            uriBuilder.queryParam(OAuth2ParameterNames.ERROR_URI, encode(error.getUri()));
        }
        if (StringUtils.hasText(authorizationRequest.getState())) {
            uriBuilder.queryParam(OAuth2ParameterNames.STATE, encode(authorizationRequest.getState()));
        }
        return uriBuilder.build(true).toUriString();
    }

    private static String encode(String value) {
        return UriUtils.encode(value, StandardCharsets.UTF_8);
    }
}

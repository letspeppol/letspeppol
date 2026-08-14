package org.letspeppol.kyc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.letspeppol.kyc.config.BrowserAuthenticationSupport;
import org.letspeppol.kyc.config.SecurityContextHelper;
import org.letspeppol.kyc.config.TotpAuthenticationSuccessHandler;
import org.letspeppol.kyc.dto.AuthErrorResponse;
import org.letspeppol.kyc.dto.AuthStatusResponse;
import org.letspeppol.kyc.dto.TotpEnableResponse;
import org.letspeppol.kyc.dto.TotpSetupResponse;
import org.letspeppol.kyc.dto.TotpStatusResponse;
import org.letspeppol.kyc.dto.TotpVerifyRequest;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.service.LoginAttemptService;
import org.letspeppol.kyc.service.TotpService;
import org.letspeppol.kyc.service.jwt.JwtClaimExtractor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@Tag(name = "KYC TOTP", description = "TOTP enrollment, recovery codes, status, and completion of a pending browser login.")
public class TotpController {

    private final TotpService totpService;
    private final JwtClaimExtractor jwtClaimExtractor;
    private final LoginAttemptService loginAttemptService;
    private final HttpSessionRequestCache requestCache;

    public TotpController(TotpService totpService, JwtClaimExtractor jwtClaimExtractor,
                          LoginAttemptService loginAttemptService) {
        this.totpService = totpService;
        this.jwtClaimExtractor = jwtClaimExtractor;
        this.loginAttemptService = loginAttemptService;
        this.requestCache = new HttpSessionRequestCache();
        this.requestCache.setMatchingRequestParameterName(null);
    }

    @PostMapping("/sapi/totp/setup")
    @Operation(summary = "Create TOTP enrollment", description = "Creates a secret and authenticator URI for the signed-in account. TOTP is not enabled until the code is verified.")
    @SecurityRequirement(name = "oauth2", scopes = "openid")
    public ResponseEntity<TotpSetupResponse> setup() {
        UUID uid = jwtClaimExtractor.extract().uid();
        return ResponseEntity.ok(totpService.generateSetup(uid));
    }

    @PostMapping("/sapi/totp/enable")
    @Operation(summary = "Enable TOTP", description = "Verifies the first authenticator code, enables TOTP, and returns one-time recovery codes.")
    @SecurityRequirement(name = "oauth2", scopes = "openid")
    public ResponseEntity<TotpEnableResponse> enable(@RequestBody TotpVerifyRequest request) {
        UUID uid = jwtClaimExtractor.extract().uid();
        return ResponseEntity.ok(totpService.verifyAndEnable(uid, request.code()));
    }

    @PostMapping("/sapi/totp/disable")
    @Operation(summary = "Disable TOTP", description = "Disables TOTP after validating a current authenticator or recovery code.")
    @SecurityRequirement(name = "oauth2", scopes = "openid")
    public ResponseEntity<Void> disable(@RequestBody TotpVerifyRequest request) {
        UUID uid = jwtClaimExtractor.extract().uid();
        totpService.disable(uid, request.code());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sapi/totp/status")
    @Operation(summary = "Get TOTP status", description = "Reports whether TOTP is enabled and how many unused recovery codes remain.")
    @SecurityRequirement(name = "oauth2", scopes = "openid")
    public ResponseEntity<TotpStatusResponse> status() {
        UUID uid = jwtClaimExtractor.extract().uid();
        return ResponseEntity.ok(totpService.getStatus(uid));
    }

    @PostMapping("/auth/totp")
    @Operation(summary = "Complete browser login with TOTP", description = "Completes the cookie-session login started by password authentication. Accepts an authenticator or recovery code and requires the CSRF token returned by `/auth/session`.")
    public ResponseEntity<?> verifyLogin(@Valid @RequestBody TotpVerifyRequest request,
                                         HttpServletRequest httpRequest,
                                         HttpServletResponse httpResponse) {
        BrowserAuthenticationSupport.preventCaching(httpResponse);
        HttpSession session = httpRequest.getSession(false);
        if (session == null) {
            return authenticationFailed();
        }

        Long accountId = (Long) session.getAttribute(TotpAuthenticationSuccessHandler.TOTP_PENDING_ACCOUNT_ID);
        if (accountId == null) {
            return authenticationFailed();
        }

        if (request == null || request.code() == null || request.code().isBlank()) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse("validation_failed"));
        }

        String attemptKey = "totp:" + accountId;
        if (loginAttemptService.isBlocked(attemptKey)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new AuthErrorResponse(KycErrorCodes.TOO_MANY_REQUESTS));
        }

        Account account = totpService.findById(accountId);
        String code = request.code().trim();
        boolean valid = totpService.verify(account, code);
        if (!valid) {
            valid = totpService.verifyRecoveryCode(account, code);
        }

        if (!valid) {
            loginAttemptService.recordFailure(attemptKey);
            return authenticationFailed();
        }

        loginAttemptService.recordSuccess(attemptKey);
        SecurityContextHelper.establishSession(account, httpRequest);
        session.removeAttribute(TotpAuthenticationSuccessHandler.TOTP_PENDING_ACCOUNT_ID);
        requestCache.removeRequest(httpRequest, httpResponse);

        return ResponseEntity.ok(new AuthStatusResponse(BrowserAuthenticationSupport.STATUS_AUTHENTICATED));
    }

    private ResponseEntity<AuthErrorResponse> authenticationFailed() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthErrorResponse(KycErrorCodes.AUTHENTCATION_FAILED));
    }
}

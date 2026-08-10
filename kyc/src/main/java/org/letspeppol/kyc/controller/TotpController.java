package org.letspeppol.kyc.controller;

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
    public ResponseEntity<TotpSetupResponse> setup() {
        UUID uid = jwtClaimExtractor.extract().uid();
        return ResponseEntity.ok(totpService.generateSetup(uid));
    }

    @PostMapping("/sapi/totp/enable")
    public ResponseEntity<TotpEnableResponse> enable(@RequestBody TotpVerifyRequest request) {
        UUID uid = jwtClaimExtractor.extract().uid();
        return ResponseEntity.ok(totpService.verifyAndEnable(uid, request.code()));
    }

    @PostMapping("/sapi/totp/disable")
    public ResponseEntity<Void> disable(@RequestBody TotpVerifyRequest request) {
        UUID uid = jwtClaimExtractor.extract().uid();
        totpService.disable(uid, request.code());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sapi/totp/status")
    public ResponseEntity<TotpStatusResponse> status() {
        UUID uid = jwtClaimExtractor.extract().uid();
        return ResponseEntity.ok(totpService.getStatus(uid));
    }

    @PostMapping("/auth/totp")
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

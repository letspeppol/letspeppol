package org.letspeppol.kyc.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.config.SecurityContextHelper;
import org.letspeppol.kyc.config.TotpAuthenticationSuccessHandler;
import org.letspeppol.kyc.dto.TotpEnableResponse;
import org.letspeppol.kyc.dto.TotpSetupResponse;
import org.letspeppol.kyc.dto.TotpStatusResponse;
import org.letspeppol.kyc.dto.TotpVerifyRequest;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.service.TotpService;
import org.letspeppol.kyc.service.jwt.JwtClaimExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
public class TotpController {

    private final TotpService totpService;
    private final JwtClaimExtractor jwtClaimExtractor;
    private final String uiBaseUrl;
    private final HttpSessionRequestCache requestCache;

    public TotpController(TotpService totpService, JwtClaimExtractor jwtClaimExtractor,
                          @Value("${UI_URL:http://localhost:9000}") String uiBaseUrl) {
        this.totpService = totpService;
        this.jwtClaimExtractor = jwtClaimExtractor;
        this.uiBaseUrl = uiBaseUrl;
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

    @PostMapping("/api/totp/verify")
    public ResponseEntity<Map<String, String>> verifyLogin(@RequestBody TotpVerifyRequest request,
                                                           HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Session expired"));
        }

        Long accountId = (Long) session.getAttribute(TotpAuthenticationSuccessHandler.TOTP_PENDING_ACCOUNT_ID);
        if (accountId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "No pending TOTP verification"));
        }

        Account account = totpService.findById(accountId);
        String code = request.code().trim();
        boolean valid = totpService.verify(account, code);
        if (!valid) {
            valid = totpService.verifyRecoveryCode(account, code);
        }

        if (!valid) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid code"));
        }

        SecurityContextHelper.establishSession(account, session);
        session.removeAttribute(TotpAuthenticationSuccessHandler.TOTP_PENDING_ACCOUNT_ID);

        SavedRequest savedRequest = requestCache.getRequest(httpRequest, null);
        String redirectUrl = savedRequest != null ? savedRequest.getRedirectUrl() : uiBaseUrl + "/login";

        log.debug("TOTP verified for account {}, redirecting to {}", accountId, redirectUrl);
        return ResponseEntity.ok(Map.of("redirectUrl", redirectUrl));
    }
}

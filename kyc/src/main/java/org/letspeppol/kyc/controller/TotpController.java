package org.letspeppol.kyc.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.config.AccountUserDetails;
import org.letspeppol.kyc.config.TotpAuthenticationSuccessHandler;
import org.letspeppol.kyc.dto.*;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.repository.AccountRepository;
import org.letspeppol.kyc.service.TotpService;
import org.letspeppol.kyc.service.jwt.JwtClaimExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
public class TotpController {

    private final TotpService totpService;
    private final JwtClaimExtractor jwtClaimExtractor;
    private final AccountRepository accountRepository;
    private final String uiBaseUrl;

    public TotpController(TotpService totpService, JwtClaimExtractor jwtClaimExtractor,
                          AccountRepository accountRepository,
                          @Value("${UI_URL:http://localhost:9000}") String uiBaseUrl) {
        this.totpService = totpService;
        this.jwtClaimExtractor = jwtClaimExtractor;
        this.accountRepository = accountRepository;
        this.uiBaseUrl = uiBaseUrl;
    }

    // --- Management endpoints (authenticated, JWT) ---

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

    // --- Login flow endpoint (unauthenticated, session-based) ---

    @PostMapping("/api/totp/verify")
    public ResponseEntity<Map<String, String>> verifyLogin(@RequestBody TotpVerifyRequest request,
                                                           HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Session expired"));
        }

        Long accountId = (Long) session.getAttribute(TotpAuthenticationSuccessHandler.TOTP_PENDING_ACCOUNT_ID);
        if (accountId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No pending TOTP verification"));
        }

        String code = request.code().trim();
        boolean valid = totpService.verify(accountId, code);
        if (!valid) {
            valid = totpService.verifyRecoveryCode(accountId, code);
        }

        if (!valid) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid code"));
        }

        // Establish full security context
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Account not found"));
        AccountUserDetails userDetails = new AccountUserDetails(account);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        session.removeAttribute(TotpAuthenticationSuccessHandler.TOTP_PENDING_ACCOUNT_ID);

        // Redirect to the saved OAuth2 authorize request, or fallback to SPA login
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setMatchingRequestParameterName(null);
        SavedRequest savedRequest = requestCache.getRequest(httpRequest, null);
        String redirectUrl = savedRequest != null ? savedRequest.getRedirectUrl() : uiBaseUrl + "/login";

        log.debug("TOTP verified for account {}, redirecting to {}", accountId, redirectUrl);
        return ResponseEntity.ok(Map.of("redirectUrl", redirectUrl));
    }
}

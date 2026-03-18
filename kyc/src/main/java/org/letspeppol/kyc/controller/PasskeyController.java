package org.letspeppol.kyc.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.config.AccountUserDetails;
import org.letspeppol.kyc.dto.*;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.service.PasskeyService;
import org.letspeppol.kyc.service.jwt.JwtClaimExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
public class PasskeyController {

    private final PasskeyService passkeyService;
    private final JwtClaimExtractor jwtClaimExtractor;
    private final String uiBaseUrl;

    public PasskeyController(PasskeyService passkeyService, JwtClaimExtractor jwtClaimExtractor,
                             @Value("${UI_URL:http://localhost:9000}") String uiBaseUrl) {
        this.passkeyService = passkeyService;
        this.jwtClaimExtractor = jwtClaimExtractor;
        this.uiBaseUrl = uiBaseUrl;
    }

    // --- Registration endpoints (authenticated, JWT) ---

    @PostMapping("/sapi/passkeys/register/options")
    public ResponseEntity<Map<String, Object>> registrationOptions(@RequestBody PasskeyRegistrationOptionsRequest request) {
        UUID uid = jwtClaimExtractor.extract().uid();
        Map<String, Object> options = passkeyService.generateRegistrationOptions(uid, request.displayName());
        return ResponseEntity.ok(options);
    }

    @PostMapping("/sapi/passkeys/register/verify")
    public ResponseEntity<Void> verifyRegistration(@RequestBody Map<String, Object> body) {
        UUID uid = jwtClaimExtractor.extract().uid();
        String challengeToken = (String) body.get("challengeToken");
        String displayName = (String) body.get("displayName");

        @SuppressWarnings("unchecked")
        Map<String, Object> credential = (Map<String, Object>) body.get("credential");

        @SuppressWarnings("unchecked")
        List<String> transports = (List<String>) credential.get("transports");

        PasskeyRegistrationResponse response = new PasskeyRegistrationResponse(
                (String) credential.get("id"),
                (String) credential.get("rawId"),
                (String) credential.get("type"),
                (String) credential.get("clientDataJSON"),
                (String) credential.get("attestationObject"),
                transports
        );

        passkeyService.verifyRegistration(uid, response, challengeToken, displayName);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/sapi/passkeys")
    public ResponseEntity<List<PasskeyDto>> listPasskeys() {
        UUID uid = jwtClaimExtractor.extract().uid();
        return ResponseEntity.ok(passkeyService.listCredentials(uid));
    }

    @DeleteMapping("/sapi/passkeys/{id}")
    public ResponseEntity<Void> deletePasskey(@PathVariable Long id) {
        UUID uid = jwtClaimExtractor.extract().uid();
        passkeyService.deleteCredential(uid, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/sapi/passkeys/{id}/name")
    public ResponseEntity<Void> renamePasskey(@PathVariable Long id, @RequestBody PasskeyRenameRequest request) {
        UUID uid = jwtClaimExtractor.extract().uid();
        passkeyService.renameCredential(uid, id, request.displayName());
        return ResponseEntity.ok().build();
    }

    // --- Authentication endpoints (unauthenticated, session-based) ---

    @PostMapping("/api/passkeys/authenticate/options")
    public ResponseEntity<Map<String, Object>> authenticationOptions(
            @RequestBody(required = false) PasskeyAuthenticationOptionsRequest request,
            HttpSession session) {
        String email = request != null ? request.email() : null;
        Map<String, Object> options = passkeyService.generateAuthenticationOptions(email, session);
        return ResponseEntity.ok(options);
    }

    @PostMapping("/api/passkeys/authenticate/verify")
    public ResponseEntity<Map<String, String>> verifyAuthentication(
            @RequestBody PasskeyAuthenticationResponse response,
            HttpServletRequest request) {
        Account account = passkeyService.verifyAuthentication(response, request.getSession());

        // Set up Spring Security context (same as form login does)
        AccountUserDetails userDetails = new AccountUserDetails(account);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);
        SecurityContextHolder.setContext(securityContext);

        // Save to session
        HttpSession session = request.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        // Redirect to the SPA login URL — the SPA will re-initiate the OAuth2 flow,
        // and since the KYC session is now authenticated, the authorize endpoint will
        // immediately issue an authorization code without showing the login page again.
        String redirectUrl = uiBaseUrl + "/login";
        log.debug("Passkey auth: redirecting to SPA login at {}", redirectUrl);

        return ResponseEntity.ok(Map.of("redirectUrl", redirectUrl));
    }
}

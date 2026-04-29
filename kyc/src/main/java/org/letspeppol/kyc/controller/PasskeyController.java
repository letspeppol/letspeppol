package org.letspeppol.kyc.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.config.SecurityContextHelper;
import org.letspeppol.kyc.dto.*;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.service.PasskeyService;
import org.letspeppol.kyc.service.jwt.JwtClaimExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @PostMapping("/sapi/passkeys/register/options")
    public ResponseEntity<Map<String, Object>> registrationOptions(@RequestBody PasskeyRegistrationOptionsRequest request) {
        UUID uid = jwtClaimExtractor.extract().uid();
        Map<String, Object> options = passkeyService.generateRegistrationOptions(uid, request.displayName());
        return ResponseEntity.ok(options);
    }

    @PostMapping("/sapi/passkeys/register/verify")
    public ResponseEntity<Void> verifyRegistration(@RequestBody PasskeyVerifyRegistrationRequest request) {
        UUID uid = jwtClaimExtractor.extract().uid();
        passkeyService.verifyRegistration(uid, request.credential(), request.challengeToken(), request.displayName());
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

        SecurityContextHelper.establishSession(account, request.getSession(true));

        // Redirect to the SPA login URL — the SPA will re-initiate the OAuth2 flow,
        // and since the KYC session is now authenticated, the authorize endpoint will
        // immediately issue an authorization code without showing the login page again.
        String redirectUrl = uiBaseUrl + "/login";
        log.debug("Passkey auth: redirecting to SPA login at {}", redirectUrl);

        return ResponseEntity.ok(Map.of("redirectUrl", redirectUrl));
    }
}

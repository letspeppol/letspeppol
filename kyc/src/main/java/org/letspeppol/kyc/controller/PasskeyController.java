package org.letspeppol.kyc.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.letspeppol.kyc.config.BrowserAuthenticationSupport;
import org.letspeppol.kyc.config.SecurityContextHelper;
import org.letspeppol.kyc.config.TotpAuthenticationSuccessHandler;
import org.letspeppol.kyc.dto.AuthStatusResponse;
import org.letspeppol.kyc.dto.AuthErrorResponse;
import org.letspeppol.kyc.dto.PasskeyAuthenticationResponse;
import org.letspeppol.kyc.dto.PasskeyDto;
import org.letspeppol.kyc.dto.PasskeyRenameRequest;
import org.letspeppol.kyc.dto.PasskeyVerifyRegistrationRequest;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.service.PasskeyService;
import org.letspeppol.kyc.service.jwt.JwtClaimExtractor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class PasskeyController {

    private final PasskeyService passkeyService;
    private final JwtClaimExtractor jwtClaimExtractor;
    private final HttpSessionRequestCache requestCache;

    public PasskeyController(PasskeyService passkeyService, JwtClaimExtractor jwtClaimExtractor) {
        this.passkeyService = passkeyService;
        this.jwtClaimExtractor = jwtClaimExtractor;
        this.requestCache = new HttpSessionRequestCache();
        this.requestCache.setMatchingRequestParameterName(null);
    }

    @PostMapping("/sapi/passkeys/register/options")
    public ResponseEntity<Map<String, Object>> registrationOptions() {
        UUID uid = jwtClaimExtractor.extract().uid();
        Map<String, Object> options = passkeyService.generateRegistrationOptions(uid);
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

    @PostMapping("/auth/passkeys/authenticate/options")
    public ResponseEntity<Map<String, Object>> authenticationOptions(
            HttpSession session, HttpServletResponse response) {
        BrowserAuthenticationSupport.preventCaching(response);
        return ResponseEntity.ok(passkeyService.generateAuthenticationOptions(session));
    }

    @PostMapping("/auth/passkeys/authenticate/verify")
    public ResponseEntity<?> verifyAuthentication(
            @Valid @RequestBody PasskeyAuthenticationResponse response,
            HttpServletRequest request,
            HttpServletResponse httpResponse) {
        BrowserAuthenticationSupport.preventCaching(httpResponse);
        if (response == null) {
            return ResponseEntity.badRequest().body(new AuthErrorResponse("validation_failed"));
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            return authenticationFailed();
        }

        Account account;
        try {
            account = passkeyService.verifyAuthentication(response, session);
        } catch (IllegalArgumentException ignored) {
            return authenticationFailed();
        }

        SecurityContextHelper.establishSession(account, request);
        request.getSession().removeAttribute(TotpAuthenticationSuccessHandler.TOTP_PENDING_ACCOUNT_ID);
        requestCache.removeRequest(request, httpResponse);

        return ResponseEntity.ok(new AuthStatusResponse(BrowserAuthenticationSupport.STATUS_AUTHENTICATED));
    }

    private ResponseEntity<AuthErrorResponse> authenticationFailed() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new AuthErrorResponse(KycErrorCodes.AUTHENTCATION_FAILED));
    }
}

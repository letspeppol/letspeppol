package org.letspeppol.kyc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.ChangePasswordRequest;
import org.letspeppol.kyc.dto.ForgotPasswordRequest;
import org.letspeppol.kyc.dto.ResetPasswordRequest;
import org.letspeppol.kyc.dto.SimpleMessage;
import org.letspeppol.kyc.service.JwtService;
import org.letspeppol.kyc.service.PasswordResetService;
import org.letspeppol.kyc.service.jwt.JwtInfo;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping()
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final JwtService jwtService;

    /// Sends password recovery mail
    @PostMapping("/api/password/forgot")
    public ResponseEntity<SimpleMessage> forgot(@Valid @RequestBody ForgotPasswordRequest request, @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        passwordResetService.requestReset(request.email(), acceptLanguage);
        return ResponseEntity.noContent().build();
    }

    /// Changes password based on password recovery mail
    @PostMapping("/api/password/reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /// Changes password based on valid credentials
    @PostMapping("/sapi/password/change") //Is sapi for early bad JWT protection
    public ResponseEntity<Void> change(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader, @Valid @RequestBody ChangePasswordRequest request) {
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        passwordResetService.changePassword(jwtInfo.uid(), request);
        return ResponseEntity.noContent().build();
    }
}

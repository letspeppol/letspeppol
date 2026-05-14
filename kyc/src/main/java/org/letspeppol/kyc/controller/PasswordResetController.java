package org.letspeppol.kyc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.ChangePasswordRequest;
import org.letspeppol.kyc.dto.ForgotPasswordRequest;
import org.letspeppol.kyc.dto.SetPasswordRequest;
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
@Tag(name = "KYC Passwords", description = "Password recovery and password change endpoints for KYC-managed accounts.")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final JwtService jwtService;

    /// Sends password recovery mail
    @PostMapping("/api/password/forgot")
    @Operation(summary = "Request password reset", description = "Starts the password recovery flow by sending a reset email to the account owner.")
    public ResponseEntity<SimpleMessage> forgot(@Valid @RequestBody ForgotPasswordRequest request, @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        passwordResetService.requestReset(request.email(), acceptLanguage);
        return ResponseEntity.noContent().build();
    }

    /// Changes password based on password recovery mail
    @PostMapping("/api/password/reset")
    @Operation(summary = "Reset password with token", description = "Completes the password recovery flow by validating the reset token and storing the new password.")
    public ResponseEntity<Void> reset(@Valid @RequestBody SetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    /// Changes password based on valid credentials
    @PostMapping("/sapi/password/change") //Is sapi for early bad JWT protection
    @Operation(summary = "Change password while signed in", description = "Changes the password for the currently authenticated account using the existing JWT session.")
    public ResponseEntity<Void> change(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader, @Valid @RequestBody ChangePasswordRequest request) {
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        passwordResetService.changePassword(jwtInfo.uid(), request);
        return ResponseEntity.noContent().build();
    }
}

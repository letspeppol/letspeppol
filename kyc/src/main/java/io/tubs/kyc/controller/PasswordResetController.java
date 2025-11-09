package io.tubs.kyc.controller;

import io.tubs.kyc.dto.ChangePasswordRequest;
import io.tubs.kyc.dto.ForgotPasswordRequest;
import io.tubs.kyc.dto.ResetPasswordRequest;
import io.tubs.kyc.dto.SimpleMessage;
import io.tubs.kyc.service.JwtService;
import io.tubs.kyc.service.PasswordResetService;
import io.tubs.kyc.service.jwt.JwtInfo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/password")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final JwtService jwtService;

    @PostMapping("/forgot")
    public ResponseEntity<SimpleMessage> forgot(@Valid @RequestBody ForgotPasswordRequest request, @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        passwordResetService.requestReset(request.email(), acceptLanguage);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change")
    public ResponseEntity<Void> change(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader, @Valid @RequestBody ChangePasswordRequest request) {
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        passwordResetService.changePassword(jwtInfo.uid(), request);
        return ResponseEntity.noContent().build();
    }
}

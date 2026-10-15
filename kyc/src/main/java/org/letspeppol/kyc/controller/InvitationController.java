package org.letspeppol.kyc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.AcceptInvitationRequest;
import org.letspeppol.kyc.dto.InvitationInfo;
import org.letspeppol.kyc.service.CompanyUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invitation")
@RequiredArgsConstructor
@Tag(name = "KYC User Invitations", description = "Public endpoints that let an invited user inspect and accept a company invitation received by email.")
public class InvitationController {

    private final CompanyUserService companyUserService;

    @PostMapping("/verify")
    @Operation(summary = "Verify invitation token", description = "Validates the invitation token and returns the invited account, the company, and whether a first password must be chosen.")
    public InvitationInfo verify(@RequestParam String token) {
        return companyUserService.verifyInvitation(token);
    }

    @PostMapping("/accept")
    @Operation(summary = "Accept invitation", description = "Activates the invited user ownership, storing the first password when the account is not verified yet, and consumes the token.")
    @ApiResponse(responseCode = "204", description = "Invitation accepted")
    public ResponseEntity<Void> accept(@Valid @RequestBody AcceptInvitationRequest request) {
        companyUserService.acceptInvitation(request);
        return ResponseEntity.noContent().build();
    }
}

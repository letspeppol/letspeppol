package org.letspeppol.kyc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.CompanyUserDto;
import org.letspeppol.kyc.dto.InviteUserRequest;
import org.letspeppol.kyc.dto.UpdateUserPermissionsRequest;
import org.letspeppol.kyc.model.Ownership;
import org.letspeppol.kyc.service.CompanyUserService;
import org.letspeppol.kyc.service.OwnershipService;
import org.letspeppol.kyc.service.jwt.JwtClaimExtractor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sapi/users")
@RequiredArgsConstructor
@Tag(name = "KYC Company Users", description = "Administrative endpoints for inviting users to a company and managing their status and permission mask.")
@SecurityRequirement(name = "oauth2", scopes = "openid")
public class CompanyUserController {

    private final CompanyUserService companyUserService;
    private final OwnershipService ownershipService;
    private final JwtClaimExtractor jwtClaimExtractor;

    @GetMapping
    @Operation(summary = "List company users", description = "Returns the admin, user and affiliate ownerships of the authenticated company with their status and effective permission mask. Intended for admin users.")
    public ResponseEntity<List<CompanyUserDto>> list() {
        return ResponseEntity.ok(companyUserService.list(activeAdmin()));
    }

    @PostMapping
    @Operation(summary = "Invite a user", description = "Creates an invited user ownership with the given permission mask and sends the invitation email. Intended for admin users.")
    public ResponseEntity<CompanyUserDto> invite(@Valid @RequestBody InviteUserRequest request, @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(companyUserService.invite(activeAdmin(), request, acceptLanguage));
    }

    @PutMapping("/{id}/permissions")
    @Operation(summary = "Update user permissions", description = "Replaces the permission mask of a user or affiliate ownership of the authenticated company and returns the normalised result. Intended for admin users.")
    public ResponseEntity<CompanyUserDto> updatePermissions(@PathVariable Long id, @Valid @RequestBody UpdateUserPermissionsRequest request) {
        return ResponseEntity.ok(companyUserService.updatePermissions(activeAdmin(), id, request.permissionMask()));
    }

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend a user", description = "Suspends an active user or affiliate ownership so it can no longer obtain tokens for the authenticated company. Intended for admin users.")
    public ResponseEntity<CompanyUserDto> suspend(@PathVariable Long id) {
        return ResponseEntity.ok(companyUserService.suspend(activeAdmin(), id));
    }

    @PostMapping("/{id}/reactivate")
    @Operation(summary = "Reactivate a user", description = "Restores a suspended user or affiliate ownership of the authenticated company. Intended for admin users.")
    public ResponseEntity<CompanyUserDto> reactivate(@PathVariable Long id) {
        return ResponseEntity.ok(companyUserService.reactivate(activeAdmin(), id));
    }

    @PostMapping("/{id}/resend")
    @Operation(summary = "Resend a user invitation", description = "Rotates the invitation token of a pending user and sends the invitation email again. Intended for admin users.")
    @ApiResponse(responseCode = "204", description = "Invitation email sent again")
    public ResponseEntity<Void> resend(@PathVariable Long id, @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        companyUserService.resendInvitation(activeAdmin(), id, acceptLanguage);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a user", description = "Removes a user ownership from the authenticated company while keeping the underlying account. Intended for admin users.")
    @ApiResponse(responseCode = "204", description = "User removed from the company")
    public ResponseEntity<Void> remove(@PathVariable Long id) {
        companyUserService.remove(activeAdmin(), id);
        return ResponseEntity.noContent().build();
    }

    private Ownership activeAdmin() {
        return ownershipService.requireActiveAdmin(jwtClaimExtractor.extract());
    }
}

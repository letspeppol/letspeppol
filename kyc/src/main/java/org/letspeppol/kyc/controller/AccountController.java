package org.letspeppol.kyc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.AuthRequest;
import org.letspeppol.kyc.dto.OwnershipSummary;
import org.letspeppol.kyc.service.OwnershipService;
import org.letspeppol.kyc.service.jwt.JwtClaimExtractor;
import org.letspeppol.kyc.service.jwt.JwtInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/sapi/account")
@RequiredArgsConstructor
@Tag(name = "KYC Account", description = "Authenticated account endpoints for discovering which companies and roles are linked to the current identity.")
@SecurityRequirement(name = "oauth2", scopes = "openid")
public class AccountController {

    private final OwnershipService ownershipService;
    private final JwtClaimExtractor jwtClaimExtractor;

    @GetMapping("/ownerships")
    @Operation(summary = "List linked ownerships", description = "Returns the companies, roles, and ownership relationships available to the currently authenticated identity.")
    public ResponseEntity<List<OwnershipSummary>> getOwnerships() {
        JwtInfo jwtInfo = jwtClaimExtractor.extract();
        return ResponseEntity.ok(ownershipService.getOwnershipSummaries(jwtInfo.uid()));
    }

    /// Changes the default company/role. The client then silently re-authorizes with this explicit
    /// ownership selection to receive a token for it.
    @PostMapping("/ownership")
    @Operation(summary = "Select acting ownership",
            description = "Remembers the given company and role as the default ownership for this account. "
                    + "The caller must then re-authorize (silently) with the same explicit selection.")
    @ApiResponse(responseCode = "204", description = "Ownership selected; re-authorize to receive an updated token")
    public ResponseEntity<Void> selectOwnership(@Valid @RequestBody AuthRequest request) {
        JwtInfo jwtInfo = jwtClaimExtractor.extract();
        ownershipService.selectOwnership(jwtInfo.uid(), request);
        return ResponseEntity.noContent().build();
    }
}

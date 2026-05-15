package org.letspeppol.kyc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.OwnershipSummary;
import org.letspeppol.kyc.service.JwtService;
import org.letspeppol.kyc.service.OwnershipService;
import org.letspeppol.kyc.service.jwt.JwtInfo;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/sapi/account")
@RequiredArgsConstructor
@Tag(name = "KYC Account", description = "Authenticated account endpoints for discovering which companies and roles are linked to the current identity.")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final JwtService jwtService;
    private final OwnershipService ownershipService;

    @GetMapping("/ownerships")
    @Operation(summary = "List linked ownerships", description = "Returns the companies, roles, and ownership relationships available to the currently authenticated identity.")
    public ResponseEntity<List<OwnershipSummary>> getOwnerships(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        return ResponseEntity.ok(ownershipService.getOwnershipSummaries(jwtInfo.uid()));
    }
}

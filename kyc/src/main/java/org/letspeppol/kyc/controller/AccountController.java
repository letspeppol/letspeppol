package org.letspeppol.kyc.controller;

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
public class AccountController {

    private final JwtService jwtService;
    private final OwnershipService ownershipService;

    @GetMapping("/ownerships")
    public ResponseEntity<List<OwnershipSummary>> getOwnerships(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        return ResponseEntity.ok(ownershipService.getOwnershipSummaries(jwtInfo.uid()));
    }
}

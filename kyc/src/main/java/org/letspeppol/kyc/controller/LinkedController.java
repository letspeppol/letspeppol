package org.letspeppol.kyc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.dto.ServiceRequest;
import org.letspeppol.kyc.exception.ForbiddenException;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.mapper.OwnershipMapper;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.service.AccountService;
import org.letspeppol.kyc.service.IdentityVerificationService;
import org.letspeppol.kyc.service.JwtService;
import org.letspeppol.kyc.service.OwnershipService;
import org.letspeppol.kyc.service.jwt.JwtInfo;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/sapi/linked")
@RequiredArgsConstructor
public class LinkedController {

    private final AccountService accountService;
    private final OwnershipService ownershipService;
    private final IdentityVerificationService identityVerificationService;
    private final JwtService jwtService;

    /// Retrieves linked info based on valid JWT token, used by App to show what users or services have access to this account
    @GetMapping
    public ResponseEntity<?> getOwnershipsForToken(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        if (jwtInfo.accountType() != AccountType.ADMIN) {
            throw new ForbiddenException(KycErrorCodes.NOT_ADMIN);
        }
        return ResponseEntity.ok(ownershipService.getByPeppolId(jwtInfo.peppolId()).stream().map(OwnershipMapper::toOwnershipInfo).toList());
    }

//    /// Create a new USER account
//    @PostMapping("/create")
//    public ResponseEntity<?> create(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader, @Valid @RequestBody NewUserRequest request) {
//        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
//        if (jwtInfo.accountType() != AccountType.ADMIN) {
//            throw new ForbiddenException(KycErrorCodes.NOT_ADMIN);
//        }
//        return ResponseEntity.ok(AccountMapper.toLinkedInfo(identityVerificationService.createUser(jwtInfo.uid(), request)));
//    }

    /// Registers a service for account
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader, @Valid @RequestBody ServiceRequest request) {
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        if (jwtInfo.accountType() != AccountType.ADMIN) {
            throw new ForbiddenException(KycErrorCodes.NOT_ADMIN);
        }
        return ResponseEntity.ok(OwnershipMapper.toOwnershipInfo(ownershipService.linkServiceToAccount(jwtInfo.peppolId(), jwtInfo.uid(), request)));
    }

    /// Unregisters a service for account
    @PostMapping("/unregister")
    public ResponseEntity<?> unregister(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader, @Valid @RequestBody ServiceRequest request) {
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        if (jwtInfo.accountType() != AccountType.ADMIN) {
            throw new ForbiddenException(KycErrorCodes.NOT_ADMIN);
        }
        ownershipService.unlinkServiceFromAccount(jwtInfo.peppolId(), jwtInfo.uid(), request);
        return ResponseEntity.noContent().build();
    }
}

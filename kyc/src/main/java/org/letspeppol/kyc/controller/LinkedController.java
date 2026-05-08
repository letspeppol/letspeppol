package org.letspeppol.kyc.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.dto.NewUserRequest;
import org.letspeppol.kyc.dto.ServiceRequest;
import org.letspeppol.kyc.exception.ForbiddenException;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.mapper.AccountMapper;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.service.AccountService;
import org.letspeppol.kyc.service.IdentityVerificationService;
import org.letspeppol.kyc.service.jwt.JwtClaimExtractor;
import org.letspeppol.kyc.service.jwt.JwtInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/sapi/linked")
@RequiredArgsConstructor
public class LinkedController {

    private final AccountService accountService;
    private final IdentityVerificationService identityVerificationService;
    private final JwtClaimExtractor jwtClaimExtractor;

    /// Retrieves linked info based on valid JWT token, used by App to show what users or services have access to this account
    @GetMapping
    public ResponseEntity<?> getLinkedForToken() {
        JwtInfo jwtInfo = jwtClaimExtractor.extract();
        if (jwtInfo.accountType() != AccountType.ADMIN) {
            throw new ForbiddenException(KycErrorCodes.NOT_ADMIN);
        }
        return ResponseEntity.ok(accountService.getAdminByExternalId(jwtInfo.uid()).getLinkedAccounts().stream().map(AccountMapper::toLinkedInfo).toList());
    }

    /// Create a new USER account
    @PostMapping("/create")
    public ResponseEntity<?> create(@Valid @RequestBody NewUserRequest request) {
        JwtInfo jwtInfo = jwtClaimExtractor.extract();
        if (jwtInfo.accountType() != AccountType.ADMIN) {
            throw new ForbiddenException(KycErrorCodes.NOT_ADMIN);
        }
        return ResponseEntity.ok(AccountMapper.toLinkedInfo(identityVerificationService.createUser(jwtInfo.uid(), request)));
    }

    /// Registers a service for account
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody ServiceRequest request) {
        JwtInfo jwtInfo = jwtClaimExtractor.extract();
        if (jwtInfo.accountType() != AccountType.ADMIN) {
            throw new ForbiddenException(KycErrorCodes.NOT_ADMIN);
        }
        return ResponseEntity.ok(AccountMapper.toLinkedInfo(accountService.linkServiceToAccount(jwtInfo.uid(), request)));
    }

    /// Unregisters a service for account
    @PostMapping("/unregister")
    public ResponseEntity<?> unregister(@Valid @RequestBody ServiceRequest request) {
        JwtInfo jwtInfo = jwtClaimExtractor.extract();
        if (jwtInfo.accountType() != AccountType.ADMIN) {
            throw new ForbiddenException(KycErrorCodes.NOT_ADMIN);
        }
        accountService.unlinkServiceFromAccount(jwtInfo.uid(), request);
        return ResponseEntity.noContent().build();
    }
}

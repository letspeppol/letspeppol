package org.letspeppol.kyc.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.RegistrationReviewDecisionResponse;
import org.letspeppol.kyc.dto.RegistrationReviewDto;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.DirectorIdentityVerification;
import org.letspeppol.kyc.model.ReviewStatus;
import org.letspeppol.kyc.service.AccountService;
import org.letspeppol.kyc.service.RegistrationReviewService;
import org.letspeppol.kyc.service.SigningService;
import org.letspeppol.kyc.service.jwt.JwtClaimExtractor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Hidden
@RestController
@RequestMapping("/sapi/backoffice/registration-reviews")
@PreAuthorize("hasAuthority('REVIEW_REGISTRATIONS')")
@RequiredArgsConstructor
public class RegistrationReviewController {

    private final RegistrationReviewService registrationReviewService;
    private final AccountService accountService;
    private final SigningService signingService;
    private final JwtClaimExtractor jwtClaimExtractor;

    @GetMapping
    public ResponseEntity<List<RegistrationReviewDto>> reviews(@RequestParam(defaultValue = "PENDING") ReviewStatus status) {
        return ResponseEntity.ok(registrationReviewService.list(status));
    }

    @GetMapping("/{id}/contract")
    public ResponseEntity<byte[]> contract(@PathVariable Long id) {
        DirectorIdentityVerification verification = registrationReviewService.get(id);
        byte[] contract = signingService.getContract(verification.getDirector().getCompany().getPeppolId(), verification.getAccount().getId());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("contract_" + id + ".pdf", StandardCharsets.UTF_8).build().toString())
                .body(contract);
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<RegistrationReviewDecisionResponse> approve(@PathVariable Long id) {
        return ResponseEntity.ok(registrationReviewService.approve(id, reviewer()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<RegistrationReviewDecisionResponse> reject(@PathVariable Long id) {
        return ResponseEntity.ok(registrationReviewService.reject(id, reviewer()));
    }

    private Account reviewer() {
        return accountService.getByExternalId(jwtClaimExtractor.extract().uid());
    }
}

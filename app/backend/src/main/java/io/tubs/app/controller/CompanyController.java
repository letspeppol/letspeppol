package io.tubs.app.controller;

import io.tubs.app.config.SecurityConfig;
import io.tubs.app.dto.CompanyDto;
import io.tubs.app.service.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/company")
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    public ResponseEntity<CompanyDto> getCompany(@AuthenticationPrincipal Jwt jwt) {
        String peppolId = jwt.getClaim(SecurityConfig.PEPPOL_ID);
        if (peppolId == null || peppolId.isBlank()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String companyNumber = peppolId.split(":")[1];
        return ResponseEntity.ok(companyService.get(companyNumber));
    }
}

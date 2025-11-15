package org.letspeppol.app.controller;

import org.letspeppol.app.dto.CompanyDto;
import org.letspeppol.app.exception.AppException;
import org.letspeppol.app.exception.AppErrorCodes;
import org.letspeppol.app.service.CompanyService;
import org.letspeppol.app.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/company")
public class CompanyController {

    private final CompanyService companyService;

    @GetMapping
    public ResponseEntity<CompanyDto> getCompany(@AuthenticationPrincipal Jwt jwt) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        String companyNumber = peppolId.split(":")[1];
        return ResponseEntity.ok(companyService.get(companyNumber));
    }

    @PutMapping
    public ResponseEntity updateCompany(@AuthenticationPrincipal Jwt jwt, @RequestBody CompanyDto companyDto) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        String companyNumber = peppolId.split(":")[1];
        if (!Objects.equals(companyDto.companyNumber(), companyNumber)) {
            log.warn("Malicious update attempt for peppolId {} company {} {}", peppolId, companyDto.companyNumber(), companyDto.name());
            throw new AppException(AppErrorCodes.PEPPOL_ID_MISMATCH);
        }
        return ResponseEntity.ok(companyService.update(companyDto));
    }
}

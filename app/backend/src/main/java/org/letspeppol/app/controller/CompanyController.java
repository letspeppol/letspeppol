package org.letspeppol.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/sapi/company")
@Tag(name = "App Company", description = "Endpoints for loading and maintaining the application-specific company profile used by the frontend.")
@SecurityRequirement(name = "bearerAuth")
public class CompanyController {

    private final CompanyService companyService;

    /// Gets Company info on login by UI (right after retrieving JWT Token)
    @GetMapping
    @Operation(summary = "Get company profile", description = "Loads the company profile displayed in the app immediately after authentication.")
    public ResponseEntity<CompanyDto> getCompany(@AuthenticationPrincipal Jwt jwt) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return ResponseEntity.ok(companyService.get(peppolId, jwt.getTokenValue(), JwtUtil.isPeppolActive(jwt)));
    }

    /// Updates Company info by UI (only stored in App)
    @PutMapping
    @Operation(summary = "Update company profile", description = "Updates application-managed company fields while enforcing that the authenticated company can only modify its own profile.")
    public ResponseEntity updateCompany(@AuthenticationPrincipal Jwt jwt, @RequestBody CompanyDto companyDto) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        if (!Objects.equals(companyDto.peppolId(), peppolId)) {
            log.warn("Malicious update attempt for peppolId {} company {} {}", peppolId, companyDto.peppolId(), companyDto.name());
            throw new AppException(AppErrorCodes.PEPPOL_ID_MISMATCH);
        }
        return ResponseEntity.ok(companyService.update(companyDto, JwtUtil.isPeppolActive(jwt), jwt.getTokenValue()));
    }
}

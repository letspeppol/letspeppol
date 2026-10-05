package org.letspeppol.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.letspeppol.app.dto.PeppolRegistrationDto;
import org.letspeppol.app.dto.PeppolSettings;
import org.letspeppol.app.service.PeppolRegistrationService;
import org.letspeppol.app.util.JwtUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@RestController
@RequestMapping("/sapi/company/peppol-registration")
@Tag(name = "App Company", description = "Endpoints for loading and maintaining the application-specific company profile used by the frontend.")
@SecurityRequirement(name = "oauth2", scopes = "openid")
public class PeppolRegistrationController {

    private final PeppolRegistrationService peppolRegistrationService;

    @GetMapping
    @PreAuthorize("hasAuthority('kyc_user')")
    @Operation(summary = "Get company Peppol registration", description = "Reads the authenticated company's current Access Point and the configured default for new registrations from the proxy.")
    public PeppolRegistrationDto getRegistration(@AuthenticationPrincipal Jwt jwt) {
        return peppolRegistrationService.get(JwtUtil.getPeppolId(jwt));
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('kyc_user')")
    @Operation(summary = "Get company receiving settings")
    public PeppolSettings getSettings(@AuthenticationPrincipal Jwt jwt) {
        return peppolRegistrationService.getSettings(JwtUtil.getPeppolId(jwt));
    }

    @GetMapping("/receiving-availability")
    @PreAuthorize("hasAuthority('kyc_user')")
    @Operation(summary = "Check receiving availability for the authenticated company", description = "Looks up the current SMP using the Peppol ID from the JWT. No company identifier parameter is accepted or required.")
    public PeppolSettings getReceivingAvailability(@AuthenticationPrincipal Jwt jwt) {
        return peppolRegistrationService.getReceivingAvailability(JwtUtil.getPeppolId(jwt));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('company_admin')")
    @Operation(summary = "Update company receiving settings")
    public PeppolSettings updateSettings(@AuthenticationPrincipal Jwt jwt, @RequestBody PeppolSettings settings) {
        if (!"ADMIN".equals(jwt.getClaimAsString("accountType"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a company administrator can change Peppol settings");
        }
        return peppolRegistrationService.updateSettings(JwtUtil.getPeppolId(jwt), settings);
    }
}

package org.letspeppol.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.letspeppol.app.dto.PeppolRegistrationDto;
import org.letspeppol.app.service.PeppolRegistrationService;
import org.letspeppol.app.util.JwtUtil;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/sapi/company/peppol-registration")
@Tag(name = "App Company", description = "Endpoints for loading and maintaining the application-specific company profile used by the frontend.")
@SecurityRequirement(name = "oauth2", scopes = "openid")
public class PeppolRegistrationController {

    private final PeppolRegistrationService peppolRegistrationService;

    @GetMapping
    @Operation(summary = "Get company Peppol registration", description = "Reads the authenticated company's current Access Point from the proxy registry.")
    public PeppolRegistrationDto getRegistration(@AuthenticationPrincipal Jwt jwt) {
        return peppolRegistrationService.get(JwtUtil.getPeppolId(jwt));
    }
}

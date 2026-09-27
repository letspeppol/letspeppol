package org.letspeppol.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.letspeppol.app.config.SponsorProperties;
import org.letspeppol.app.dto.SponsorContributionDto;
import org.letspeppol.app.dto.SponsorInvoiceResponse;
import org.letspeppol.app.dto.SponsorInvoiceRequest;
import org.letspeppol.app.dto.SponsorDto;
import org.letspeppol.app.dto.SponsorsResponseDto;
import org.letspeppol.app.service.SponsorInvoiceService;
import org.letspeppol.app.util.JwtUtil;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@Tag(name = "App Sponsors", description = "Public sponsor information and authenticated sponsor-invoice creation.")
public class SponsorController {

    private final SponsorProperties sponsorProperties;
    private final SponsorInvoiceService sponsorInvoiceService;

    @GetMapping("/api/sponsors")
    @Operation(summary = "List sponsors", description = "Returns public sponsor names, logo URLs, and website links configured for the project website.")
    public SponsorsResponseDto getSponsors() {
        List<SponsorDto> sponsors = sponsorProperties.getList().stream()
                .map(s -> new SponsorDto(
                        s.getName(),
                        sponsorProperties.getBaseUrl() + s.getLogo(),
                        s.getUrl()
                ))
                .toList();
        return new SponsorsResponseDto(sponsors);
    }

    @GetMapping("/api/sponsors/contributions")
    @Operation(summary = "List sponsor contributions", description = "Returns public aggregate sponsor contribution information derived from sponsor invoices.")
    public List<SponsorContributionDto> getSponsorContributions() {
        return sponsorInvoiceService.getSponsorContributions();
    }

    @PostMapping("/sapi/sponsors")
    @Operation(summary = "Create sponsor invoice", description = "Creates and submits a sponsor invoice for the authenticated company. App obtains its downstream Proxy token with client credentials and forwards the user token as acting-user context.")
    @SecurityRequirement(name = "oauth2", scopes = "openid")
    public SponsorInvoiceResponse createSponsorInvoice(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody SponsorInvoiceRequest request) {
        String customerPeppolId = JwtUtil.getPeppolId(jwt);
        return sponsorInvoiceService.createSponsorInvoice(customerPeppolId, request, jwt.getTokenValue());
    }
}


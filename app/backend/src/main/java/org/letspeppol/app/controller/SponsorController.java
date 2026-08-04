package org.letspeppol.app.controller;

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
public class SponsorController {

    private final SponsorProperties sponsorProperties;
    private final SponsorInvoiceService sponsorInvoiceService;

    @GetMapping("/api/sponsors")
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
    public List<SponsorContributionDto> getSponsorContributions() {
        return sponsorInvoiceService.getSponsorContributions();
    }

    @PostMapping("/sapi/sponsors")
    public SponsorInvoiceResponse createSponsorInvoice(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody SponsorInvoiceRequest request) {
        String customerPeppolId = JwtUtil.getPeppolId(jwt);
        return sponsorInvoiceService.createSponsorInvoice(customerPeppolId, request, jwt.getTokenValue());
    }
}


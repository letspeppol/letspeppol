package org.letspeppol.app.controller;

import lombok.RequiredArgsConstructor;
import org.letspeppol.app.config.SponsorProperties;
import org.letspeppol.app.dto.SponsorDto;
import org.letspeppol.app.dto.SponsorsResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/sponsors")
public class SponsorController {

    private final SponsorProperties sponsorProperties;

    @GetMapping
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
}


package org.letspeppol.app.controller;

import org.letspeppol.app.dto.DonationStatsDto;
import org.letspeppol.app.dto.OpenCollectiveAccountDto;
import org.letspeppol.app.service.DonationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/donation")
public class DonationController {

    private final DonationService donationService;

    @GetMapping("stats")
    public DonationStatsDto getOpenCollectiveAccount() {
        return donationService.getDonationStats();
    }
}

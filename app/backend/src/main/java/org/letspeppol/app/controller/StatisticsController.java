package org.letspeppol.app.controller;

import org.letspeppol.app.dto.DonationStatsDto;
import org.letspeppol.app.dto.TotalsDto;
import org.letspeppol.app.service.DonationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.app.service.StatisticsService;
import org.letspeppol.app.util.JwtUtil;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("")
public class StatisticsController {

    private final DonationService donationService;
    private final StatisticsService statisticsService;

    @GetMapping("/api/stats/donation")
    public DonationStatsDto getOpenCollectiveAccount() {
        return donationService.getDonationStats();
    }

    @GetMapping("/sapi/stats/account")
    public TotalsDto getAccountTotals(@AuthenticationPrincipal Jwt jwt) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return statisticsService.getTotals(peppolId);
    }
}

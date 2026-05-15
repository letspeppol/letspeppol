package org.letspeppol.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "App Statistics", description = "Endpoints for public donation statistics and authenticated account-level dashboard totals.")
public class StatisticsController {

    private final DonationService donationService;
    private final StatisticsService statisticsService;

    @GetMapping("/api/stats/donation")
    @Operation(summary = "Get donation statistics", description = "Returns public donation metrics used to display project funding information.")
    public DonationStatsDto getOpenCollectiveAccount() {
        return donationService.getDonationStats();
    }

    @GetMapping("/sapi/stats/account")
    @Operation(summary = "Get account dashboard totals", description = "Returns authenticated dashboard totals for the current company account.")
    @SecurityRequirement(name = "bearerAuth")
    public TotalsDto getAccountTotals(@AuthenticationPrincipal Jwt jwt) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return statisticsService.getTotals(peppolId);
    }
}

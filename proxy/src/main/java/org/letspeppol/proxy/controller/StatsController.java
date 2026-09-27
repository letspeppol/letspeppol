package org.letspeppol.proxy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.letspeppol.proxy.dto.ProxyStatsDto;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.repository.RegistryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/stats")
@Tag(name = "Proxy Statistics", description = "Public aggregate proxy usage counters; no company or document data is returned.")
public class StatsController {

    private final RegistryRepository registryRepository;

    @GetMapping
    @Operation(summary = "Get proxy usage statistics", description = "Returns the number of registry entries using a configured access point.")
    public ProxyStatsDto getStats() {
        return new ProxyStatsDto(registryRepository.countByAccessPointNot(AccessPoint.NONE));
    }
}

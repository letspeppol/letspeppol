package org.letspeppol.proxy.controller;

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
public class StatsController {

    private final RegistryRepository registryRepository;

    @GetMapping
    public ProxyStatsDto getStats() {
        return new ProxyStatsDto(registryRepository.countByAccessPointNot(AccessPoint.NONE));
    }
}

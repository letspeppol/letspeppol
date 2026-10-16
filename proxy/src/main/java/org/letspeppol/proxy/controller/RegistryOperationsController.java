package org.letspeppol.proxy.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.proxy.dto.RegistrationRequest;
import org.letspeppol.proxy.exception.NotFoundException;
import org.letspeppol.proxy.mapper.RegistryMapper;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.repository.RegistryRepository;
import org.letspeppol.proxy.service.AccessPointServiceRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Hidden
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/lapi/registry")
public class RegistryOperationsController {

    private final RegistryRepository registryRepository;
    private final AccessPointServiceRegistry accessPoints;

    @Value("${proxy.access-point}")
    private AccessPoint defaultAccessPoint;

    @PostMapping("/{peppolId}/move")
    public synchronized ResponseEntity<?> move(@PathVariable String peppolId,
                                              @RequestParam(required = false) String accessPoint,
                                              @RequestBody RegistrationRequest data) {
        AccessPoint targetAccessPoint;
        try {
            targetAccessPoint = accessPoint == null ? defaultAccessPoint : AccessPoint.valueOf(accessPoint);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid accessPoint parameter");
        }
        var target = accessPoints.get(targetAccessPoint);
        if (target == null) {
            return ResponseEntity.badRequest().body("The target access point is not available");
        }
        var registry = registryRepository.findById(peppolId)
                .orElseThrow(() -> new NotFoundException("Peppol ID is not registered here"));
        var previousAccessPoint = registry.getAccessPoint();
        if (previousAccessPoint == targetAccessPoint) {
            return ResponseEntity.ok(RegistryMapper.toDto(registry));
        }
        var source = accessPoints.get(previousAccessPoint);
        if (source == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("An active, available source access point is required");
        }
        try {
            target.validateRegistration(peppolId, data);
            source.validateRegistration(peppolId, data);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        var previousVariables = registry.getVariables();
        source.unregister(peppolId, previousVariables);
        try {
            registry.setVariables(target.register(peppolId, data));
            registry.setAccessPoint(targetAccessPoint);
            return ResponseEntity.ok(RegistryMapper.toDto(registryRepository.save(registry)));
        } catch (RuntimeException failure) {
            log.error("Operator move to {} failed for {}", targetAccessPoint, peppolId, failure);
            if (registry.getAccessPoint() == targetAccessPoint) {
                try {
                    target.unregister(peppolId, registry.getVariables());
                } catch (RuntimeException cleanupFailure) {
                    log.error("Failed to clean up {} registration for {}", targetAccessPoint, peppolId, cleanupFailure);
                }
            }
            registry.setVariables(previousVariables);
            registry.setAccessPoint(AccessPoint.NONE);
            try {
                registry.setVariables(source.register(peppolId, data));
                registry.setAccessPoint(previousAccessPoint);
            } catch (RuntimeException recoveryFailure) {
                log.error("Failed to restore {} registration for {}", previousAccessPoint, peppolId, recoveryFailure);
            }
            return ResponseEntity.status(HttpStatus.FAILED_DEPENDENCY)
                    .body(RegistryMapper.toDto(registryRepository.save(registry)));
        }
    }
}

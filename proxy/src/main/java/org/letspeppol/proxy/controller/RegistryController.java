package org.letspeppol.proxy.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.proxy.dto.AppLinkRequest;
import org.letspeppol.proxy.dto.RegistrationRequest;
import org.letspeppol.proxy.dto.RegistryDto;
import org.letspeppol.proxy.exception.NotFoundException;
import org.letspeppol.proxy.mapper.RegistryMapper;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.repository.RegistryRepository;
import org.letspeppol.proxy.service.AccessPointServiceRegistry;
import org.letspeppol.proxy.service.AppLinkService;
import org.letspeppol.proxy.service.RegistryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Registry endpoints are reachable only by trusted backend services (ROLE_SERVICE). The acting
 * user's peppolId is asserted by the caller (KYC, after its own ADMIN / contract gating) as a
 * request parameter rather than read from the token, because the service token does not represent
 * an individual end user.
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/sapi/registry")
@Tag(name = "Proxy Registry", description = "Service-facing endpoints for managing a company's proxy registration and access-point level app links.")
@SecurityRequirement(name = "serviceAuth", scopes = "service")
public class RegistryController {

    private final AppLinkService appLinkService;
    private final RegistryService registryService;
    private final RegistryRepository registryRepository;
    private final AccessPointServiceRegistry accessPoints;

    @Value("${proxy.access-point}")
    private AccessPoint accessPoint;

    @GetMapping()
    @Operation(summary = "Get registry state", description = "Returns the current proxy registration state for the given company.")
    public RegistryDto getById(@RequestParam String peppolId) {
        return registryService.get(peppolId);
    }

    @PostMapping()
    @Operation(summary = "Register company on access point", description = "Creates or updates proxy registration for the given company using the configured access point.")
    public ResponseEntity<RegistryDto> register(@RequestParam String peppolId, @RequestBody RegistrationRequest data) {
        return ResponseEntity.status(HttpStatus.OK).body(registryService.register(
            peppolId,
            data,
            accessPoint
        ));
    }

    @Hidden
    @PostMapping("/{peppolId}/move")
    public synchronized ResponseEntity<?> move(@PathVariable String peppolId,
                                              @RequestParam(required = false) String accessPoint,
                                              @RequestBody RegistrationRequest data) {
        AccessPoint targetAccessPoint;
        try {
            targetAccessPoint = accessPoint == null ? this.accessPoint : AccessPoint.valueOf(accessPoint);
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

    @PutMapping("unregister")
    @Operation(summary = "Unregister company from access point", description = "Disables the proxy registration for the given company while preserving the stored record.")
    public ResponseEntity<RegistryDto> unregister(@RequestParam String peppolId) {
        return ResponseEntity.status(HttpStatus.OK).body(registryService.unregister(peppolId));
    }

    @PutMapping("allow")
    @Operation(summary = "Allow app link", description = "Approves an app identity so it can act on behalf of the given company through the proxy.")
    public ResponseEntity<Void> allow(@RequestParam String peppolId, @RequestBody AppLinkRequest data) {
        appLinkService.add(peppolId, data.uid());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("reject")
    @Operation(summary = "Reject app link", description = "Revokes or rejects an app identity that should no longer act on behalf of the given company.")
    public ResponseEntity<Void> reject(@RequestParam String peppolId, @RequestBody AppLinkRequest data) {
        appLinkService.remove(peppolId, data.uid());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping()
    @Operation(summary = "Delete registry record", description = "Removes the stored proxy registry entry for the given company.")
    public ResponseEntity<Void> delete(@RequestParam String peppolId) {
        registryService.remove(peppolId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}

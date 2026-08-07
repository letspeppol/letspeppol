package org.letspeppol.proxy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.letspeppol.proxy.dto.AppLinkRequest;
import org.letspeppol.proxy.dto.RegistrationRequest;
import org.letspeppol.proxy.dto.RegistryDto;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.service.AppLinkService;
import org.letspeppol.proxy.service.RegistryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Registry endpoints are reachable only by trusted backend services (ROLE_SERVICE). The acting
 * user's peppolId is asserted by the caller (KYC, after its own ADMIN / contract gating) as a
 * request parameter rather than read from the token, because the service token does not represent
 * an individual end user.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/sapi/registry")
@Tag(name = "Proxy Registry", description = "Service-facing endpoints for managing a company's proxy registration and access-point level app links.")
@SecurityRequirement(name = "bearerAuth")
public class RegistryController {

    private final AppLinkService appLinkService;
    private final RegistryService registryService;

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
            AccessPoint.SCRADA
        ));
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

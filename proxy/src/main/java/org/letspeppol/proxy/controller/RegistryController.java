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
import org.letspeppol.proxy.util.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/sapi/registry")
@Tag(name = "Proxy Registry", description = "Service-facing endpoints for managing a company's proxy registration and access-point level app links.")
@SecurityRequirement(name = "bearerAuth")
public class RegistryController {

    private final AppLinkService appLinkService;
    private final RegistryService registryService;

    @GetMapping()
    @Operation(summary = "Get registry state", description = "Returns the current proxy registration state for the authenticated company.")
    public RegistryDto getById(@AuthenticationPrincipal Jwt jwt) {
        String peppolId = JwtUtil.getUserPeppolId(jwt);
        return registryService.get(peppolId);
    }

    @PostMapping()
    @Operation(summary = "Register company on access point", description = "Creates or updates proxy registration for the authenticated company using the configured access point.")
    public ResponseEntity<RegistryDto> register(@AuthenticationPrincipal Jwt jwt, @RequestBody RegistrationRequest data) {
        String peppolId = JwtUtil.getUserPeppolId(jwt);
        return ResponseEntity.status(HttpStatus.OK).body(registryService.register(
            peppolId,
            data,
            AccessPoint.SCRADA
        ));
    }

    @PutMapping("unregister")
    @Operation(summary = "Unregister company from access point", description = "Disables the proxy registration for the authenticated company while preserving the stored record.")
    public ResponseEntity<RegistryDto> unregister(@AuthenticationPrincipal Jwt jwt) {
        String peppolId = JwtUtil.getUserPeppolId(jwt);
        return ResponseEntity.status(HttpStatus.OK).body(registryService.unregister(peppolId));
    }

    @PutMapping("allow")
    @Operation(summary = "Allow app link", description = "Approves an app identity so it can act on behalf of the authenticated company through the proxy.")
    public ResponseEntity<Void> allow(@AuthenticationPrincipal Jwt jwt, @RequestBody AppLinkRequest data) {
        String peppolId = JwtUtil.getUserPeppolId(jwt);
        appLinkService.add(peppolId, data.uid());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("reject")
    @Operation(summary = "Reject app link", description = "Revokes or rejects an app identity that should no longer act on behalf of the authenticated company.")
    public ResponseEntity<Void> reject(@AuthenticationPrincipal Jwt jwt, @RequestBody AppLinkRequest data) {
        String peppolId = JwtUtil.getUserPeppolId(jwt);
        appLinkService.remove(peppolId, data.uid());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping()
    @Operation(summary = "Delete registry record", description = "Removes the stored proxy registry entry for the authenticated company.")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt) {
        String peppolId = JwtUtil.getUserPeppolId(jwt);
        registryService.remove(peppolId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}

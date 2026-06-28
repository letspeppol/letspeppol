package org.letspeppol.proxy.controller;

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
public class RegistryController {

    private final AppLinkService appLinkService;
    private final RegistryService registryService;

    @GetMapping()
    public RegistryDto getById(@RequestParam String peppolId) {
        return registryService.get(peppolId);
    }

    @PostMapping()
    public ResponseEntity<RegistryDto> register(@RequestParam String peppolId, @RequestBody RegistrationRequest data) {
        return ResponseEntity.status(HttpStatus.OK).body(registryService.register(
            peppolId,
            data,
            AccessPoint.SCRADA
        ));
    }

    @PutMapping("unregister")
    public ResponseEntity<RegistryDto> unregister(@RequestParam String peppolId) {
        return ResponseEntity.status(HttpStatus.OK).body(registryService.unregister(peppolId));
    }

    @PutMapping("allow")
    public ResponseEntity<?> allow(@RequestParam String peppolId, @RequestBody AppLinkRequest data) {
        appLinkService.add(peppolId, data.uid());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("reject")
    public ResponseEntity<?> reject(@RequestParam String peppolId, @RequestBody AppLinkRequest data) {
        appLinkService.remove(peppolId, data.uid());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping()
    public ResponseEntity<Object> delete(@RequestParam String peppolId) {
        registryService.remove(peppolId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}

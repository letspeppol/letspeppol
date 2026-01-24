package org.letspeppol.proxy.controller;

import lombok.RequiredArgsConstructor;
import org.letspeppol.proxy.dto.RegistrationRequest;
import org.letspeppol.proxy.dto.RegistryDto;
import org.letspeppol.proxy.model.AccessPoint;
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
public class RegistryController {

    private final RegistryService registryService;

    @GetMapping()
    public RegistryDto getById(@AuthenticationPrincipal Jwt jwt) {
        String peppolId = JwtUtil.getUserPeppolId(jwt);
        return registryService.get(peppolId);
    }

    @PostMapping()
    public ResponseEntity<RegistryDto> register(@AuthenticationPrincipal Jwt jwt, @RequestBody RegistrationRequest data) {
        String peppolId = JwtUtil.getUserPeppolId(jwt);
        return ResponseEntity.status(HttpStatus.OK).body(registryService.register(
            peppolId,
            data,
            AccessPoint.SCRADA
        ));
    }

    @PutMapping("unregister")
    public ResponseEntity<RegistryDto> unregister(@AuthenticationPrincipal Jwt jwt) {
        String peppolId = JwtUtil.getUserPeppolId(jwt);
        return ResponseEntity.status(HttpStatus.OK).body(registryService.unregister(peppolId));
    }

    @DeleteMapping()
    public ResponseEntity<Object> delete(@AuthenticationPrincipal Jwt jwt) {
        String peppolId = JwtUtil.getUserPeppolId(jwt);
        registryService.remove(peppolId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}

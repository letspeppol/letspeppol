package org.letspeppol.proxy.controller;

import lombok.RequiredArgsConstructor;
import org.letspeppol.proxy.dto.RegistrationRequest;
import org.letspeppol.proxy.dto.RegistryDto;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.service.RegistryService;
import org.letspeppol.proxy.util.JwtUtil;
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
        String peppolId = JwtUtil.getPeppolId(jwt);
        return registryService.get(peppolId);
    }

    @PostMapping()
    public RegistryDto register(@AuthenticationPrincipal Jwt jwt, @RequestBody RegistrationRequest data) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return registryService.register(
            peppolId,
            data,
            AccessPoint.SCRADA
        );
    }

    @PutMapping("suspend")
    public RegistryDto suspend(@AuthenticationPrincipal Jwt jwt) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return registryService.suspend(peppolId);
    }

    @PutMapping("activate")
    public RegistryDto activate(@AuthenticationPrincipal Jwt jwt, @RequestBody RegistrationRequest data) { //TODO : is this useful or confusing ?
        String peppolId = JwtUtil.getPeppolId(jwt);
        return registryService.activate(
                peppolId,
                data,
                AccessPoint.SCRADA
        );
    }

    @DeleteMapping()
    public void delete(@AuthenticationPrincipal Jwt jwt) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        registryService.remove(peppolId);
    }
}

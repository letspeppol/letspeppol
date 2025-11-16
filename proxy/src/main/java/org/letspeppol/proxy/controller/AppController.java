package org.letspeppol.proxy.controller;

import lombok.RequiredArgsConstructor;
import org.letspeppol.proxy.dto.UblDocumentDto;
import org.letspeppol.proxy.exception.SecurityException;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.service.RegistryService;
import org.letspeppol.proxy.service.UblDocumentService;
import org.letspeppol.proxy.util.JwtUtil;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("")
public class AppController {

    public static final String DEFAULT_SIZE = "10";

    private final UblDocumentService ublDocumentService;
    private final RegistryService registryService;

    @GetMapping("/")
    public List<UblDocumentDto> getAllNew(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = DEFAULT_SIZE) int size) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return ublDocumentService.findAllNew(peppolId, size);
    }

    @GetMapping("/{id}")
    public UblDocumentDto getById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return ublDocumentService.findById(id, peppolId);
    }

    @PostMapping("/")
    public UblDocumentDto createToSend(@AuthenticationPrincipal Jwt jwt, @RequestBody UblDocumentDto ublDocumentDto, @RequestParam(defaultValue = "false") boolean noArchive) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        if (!ublDocumentDto.ownerPeppolId().equals(peppolId)) throw new SecurityException("Peppol ID not the owner");
        if (registryService.getAccessPoint(peppolId) == AccessPoint.NONE) throw new SecurityException("Peppol ID not registered to send");
        return ublDocumentService.createToSend(ublDocumentDto, noArchive); //TODO : maybe something like return ResponseEntity.status(HttpStatus.CREATED).body(dto); ?
    }

    @PutMapping("/{id}")
    public void reschedule(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody UblDocumentDto ublDocumentDto) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        if (!ublDocumentDto.ownerPeppolId().equals(peppolId)) throw new SecurityException("Peppol ID not the owner");
        if (registryService.getAccessPoint(peppolId) == AccessPoint.NONE) throw new SecurityException("Peppol ID not registered to send");
        ublDocumentService.reschedule(id, peppolId, ublDocumentDto);
    }

    @PutMapping("/downloaded/{id}")
    public void downloaded(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean noArchive) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        ublDocumentService.downloaded(List.of(id), peppolId, noArchive);
    }

    @PutMapping("/downloaded")
    public void downloadedBatch(@AuthenticationPrincipal Jwt jwt, @RequestBody List<UUID> ids, @RequestParam(defaultValue = "false") boolean noArchive) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        ublDocumentService.downloaded(ids, peppolId, noArchive);
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam(defaultValue = "false") boolean noArchive) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        ublDocumentService.cancel(id, peppolId, noArchive);
    }

}

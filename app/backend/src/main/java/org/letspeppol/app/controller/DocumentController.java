package org.letspeppol.app.controller;

import lombok.RequiredArgsConstructor;
import org.letspeppol.app.dto.DocumentDto;
import org.letspeppol.app.service.DocumentService;
import org.letspeppol.app.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/document")
public class DocumentController {

    @Autowired
    private final DocumentService documentService;

    @GetMapping("/")
    public List<DocumentDto> getAll(@AuthenticationPrincipal Jwt jwt) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return documentService.findAll(peppolId);
    }

    @GetMapping("/{id}")
    public DocumentDto getById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return documentService.findById(peppolId, id);
    }

    @PostMapping("/")
    public DocumentDto create(@AuthenticationPrincipal Jwt jwt, @RequestBody String ublXml, @RequestParam(required = false) boolean draft, @RequestParam(required = false) Instant schedule) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return documentService.createFromUbl(peppolId, ublXml, draft, schedule);
    }

    @PutMapping("/{id}")
    public DocumentDto update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody DocumentDto documentDto, @RequestParam(required = false) boolean draft, @RequestParam(required = false) Instant schedule) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return documentService.update(peppolId, id, documentDto, draft, schedule);
    }

    @PutMapping("/{id}/send")
    public DocumentDto send(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam(required = false) Instant schedule) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return documentService.send(peppolId, id, schedule);
    }

    @PutMapping("/{id}/read")
    public DocumentDto read(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return documentService.read(peppolId, id);
    }

    @PutMapping("/{id}/paid")
    public DocumentDto paid(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return documentService.paid(peppolId, id);
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        documentService.delete(peppolId, id);
    }
}

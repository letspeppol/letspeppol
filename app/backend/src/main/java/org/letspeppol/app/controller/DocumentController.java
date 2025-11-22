package org.letspeppol.app.controller;

import lombok.RequiredArgsConstructor;
import org.letspeppol.app.dto.DocumentDto;
import org.letspeppol.app.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
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
        return documentService.findAll();
    }

    @GetMapping("/{id}")
    public DocumentDto getById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return documentService.findById(id);
    }

//    @PostMapping("/")
//    public DocumentDto create(@AuthenticationPrincipal Jwt jwt, @RequestBody String ublXml) {
//        return documentService.createFromUbl(ublXml);
//    }

    @PutMapping("/{id}")
    public DocumentDto update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody DocumentDto documentDto) {
        return documentService.update(id, documentDto);
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        documentService.delete(id);
    }
}

package org.letspeppol.app.controller;

import lombok.RequiredArgsConstructor;
import org.letspeppol.app.dto.DocumentDto;
import org.letspeppol.app.dto.DocumentFilter;
import org.letspeppol.app.dto.PageResponse;
import org.letspeppol.app.dto.ValidationResultDto;
import org.letspeppol.app.exception.PeppolException;
import org.letspeppol.app.model.DocumentDirection;
import org.letspeppol.app.model.DocumentType;
import org.letspeppol.app.service.DocumentService;
import org.letspeppol.app.service.ValidationService;
import org.letspeppol.app.service.UblInvoicePdfService;
import org.letspeppol.app.util.JwtUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/sapi/document")
public class DocumentController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_UBL_BYTES = 10 * 1024 * 1024; // 10 MB

    private final DocumentService documentService;
    private final ValidationService validationService;
    private final UblInvoicePdfService ublInvoicePdfService;

    @PostMapping("validate")
    public ResponseEntity<?> validate(@RequestBody String ublXml) {
        if (ublXml == null || ublXml.isBlank()) {
            return ResponseEntity.badRequest().body("Missing XML content");
        }
        rejectIfUblTooLarge(ublXml);
        ValidationResultDto response = validationService.validateUblXml(ublXml);
        return ResponseEntity.ok(response);
    }

    @GetMapping()
    public PageResponse<DocumentDto> getAll(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(required = false) DocumentType type,
                                    @RequestParam(required = false) DocumentDirection direction,
                                    @RequestParam(required = false) String partnerName,
                                    @RequestParam(required = false) String partnerPeppolId,
                                    @RequestParam(required = false) String invoiceReference,
                                    @RequestParam(required = false) Boolean paid,
                                    @RequestParam(required = false) Boolean read,
                                    @RequestParam(required = false) Boolean draft,
                                    Pageable pageable
    ) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        DocumentFilter filter = new DocumentFilter();
        filter.setOwnerPeppolId(peppolId);
        filter.setType(type);
        filter.setDirection(direction);
        filter.setPartnerName(partnerName != null && !partnerName.isBlank() ? partnerName.trim() : null);
        filter.setPartnerPeppolId(partnerPeppolId != null && !partnerPeppolId.isBlank() ? partnerPeppolId.trim() : null);
        filter.setInvoiceReference(invoiceReference != null && !invoiceReference.isBlank() ? invoiceReference.trim() : null);
        filter.setPaid(paid);
        filter.setRead(read);
        filter.setDraft(draft);
        Pageable cappedPageable = capPageSize(pageable);
        Page<DocumentDto> page = documentService.findAll(filter, cappedPageable);
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    private void rejectIfUblTooLarge(String ublXml) {
        if (ublXml != null && ublXml.getBytes(StandardCharsets.UTF_8).length > MAX_UBL_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "UBL XML exceeds the maximum allowed size");
        }
    }

    private Pageable capPageSize(Pageable pageable) {
        if (pageable == null || pageable.getPageSize() <= MAX_PAGE_SIZE) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }

    @GetMapping("{id}")
    public DocumentDto getById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return documentService.findById(peppolId, id);
    }

    @PostMapping()
    public DocumentDto create(@AuthenticationPrincipal Jwt jwt,
                              @RequestBody String ublXml,
                              @RequestParam(required = false) boolean draft,
                              @RequestParam(required = false) Instant schedule,
                              @RequestParam(required = false, defaultValue = "true") boolean createdExternally) {
        rejectIfUblTooLarge(ublXml);
        if (!JwtUtil.isPeppolActive(jwt)) {
            draft = true;
        }
        String peppolId = JwtUtil.getPeppolId(jwt);
        return documentService.createFromUbl(peppolId, ublXml, draft, schedule, createdExternally, jwt.getTokenValue());
    }

    @PutMapping("{id}")
    public DocumentDto update(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID id,
                              @RequestBody String ublXml,
                              @RequestParam(required = false) boolean draft,
                              @RequestParam(required = false) Instant schedule) {
        rejectIfUblTooLarge(ublXml);
        if (!JwtUtil.isPeppolActive(jwt)) {
            draft = true;
        }
        String peppolId = JwtUtil.getPeppolId(jwt);
        return documentService.update(peppolId, id, ublXml, draft, schedule, jwt.getTokenValue());
    }

    @PutMapping("{id}/send")
    public DocumentDto send(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam(required = false) Instant schedule) {
        if (!JwtUtil.isPeppolActive(jwt)) {
            throw new PeppolException("Peppol ID is not active");
        }
        String peppolId = JwtUtil.getPeppolId(jwt);
        return documentService.send(peppolId, id, schedule, jwt.getTokenValue());
    }

    @PutMapping("{id}/reschedule")
    public DocumentDto reschedule(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam(required = false) Instant schedule) {
        if (!JwtUtil.isPeppolActive(jwt)) {
            throw new PeppolException("Peppol ID is not active");
        }
        String peppolId = JwtUtil.getPeppolId(jwt);
        return documentService.reschedule(peppolId, id, schedule, jwt.getTokenValue());
    }

    @PutMapping("{id}/read")
    public DocumentDto read(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return documentService.read(peppolId, id);
    }

    @PutMapping("{id}/paid")
    public DocumentDto paid(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return documentService.paid(peppolId, id);
    }

    @DeleteMapping("{id}")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        documentService.delete(peppolId, id);
    }

    @GetMapping("{id}/pdf")
    public ResponseEntity<byte[]> getPdf(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID id,
                                         @RequestParam(required = false, defaultValue = "FINAL") UblInvoicePdfService.RenderMode mode) { // Will be used later for proforma
        String peppolId = JwtUtil.getPeppolId(jwt);
        DocumentDto doc = documentService.findById(peppolId, id);
        UblInvoicePdfService.RenderMode renderMode = doc.scheduledOn() == null ? UblInvoicePdfService.RenderMode.DRAFT : UblInvoicePdfService.RenderMode.FINAL;
        byte[] pdf = ublInvoicePdfService.toPdf(doc.ubl(), renderMode);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"invoice-" + id + ".pdf\"")
                .body(pdf);
    }
}

package org.letspeppol.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/sapi/document")
@Tag(name = "App Documents", description = "Endpoints for validating, listing, editing, sending, and rendering business documents inside the application.")
@SecurityRequirement(name = "bearerAuth")
public class DocumentController {

    private final DocumentService documentService;
    private final ValidationService validationService;
    private final UblInvoicePdfService ublInvoicePdfService;

    @PostMapping("validate")
    @Operation(summary = "Validate UBL XML", description = "Checks whether a raw UBL XML payload is structurally and semantically valid before it is stored or sent.")
    public ResponseEntity<?> validate(@RequestBody String ublXml) {
        if (ublXml == null || ublXml.isBlank()) {
            return ResponseEntity.badRequest().body("Missing XML content");
        }
        ValidationResultDto response = validationService.validateUblXml(ublXml);
        return ResponseEntity.ok(response);
    }

    @GetMapping()
    @Operation(summary = "List documents", description = "Returns the authenticated company's documents with optional filters for type, direction, partner, and workflow state.")
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
        Page<DocumentDto> page = documentService.findAll(filter, pageable);
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }

    @GetMapping("{id}")
    @Operation(summary = "Get document by id", description = "Loads one stored document visible to the authenticated company.")
    public DocumentDto getById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return documentService.findById(peppolId, id);
    }

    @PostMapping()
    @Operation(summary = "Create document", description = "Creates a new document from UBL XML. If the company is not yet Peppol-active, the document is forced into draft mode.")
    public DocumentDto create(@AuthenticationPrincipal Jwt jwt,
                              @RequestBody String ublXml,
                              @RequestParam(required = false) boolean draft,
                              @RequestParam(required = false) Instant schedule,
                              @RequestParam(required = false, defaultValue = "true") boolean createdExternally) {
        if (!JwtUtil.isPeppolActive(jwt)) {
            draft = true;
        }
        String peppolId = JwtUtil.getPeppolId(jwt);
        return documentService.createFromUbl(peppolId, ublXml, draft, schedule, createdExternally, jwt.getTokenValue());
    }

    @PutMapping("{id}")
    @Operation(summary = "Update document", description = "Updates an existing document's UBL payload and send scheduling information.")
    public DocumentDto update(@AuthenticationPrincipal Jwt jwt,
                              @PathVariable UUID id,
                              @RequestBody String ublXml,
                              @RequestParam(required = false) boolean draft,
                              @RequestParam(required = false) Instant schedule) {
        if (!JwtUtil.isPeppolActive(jwt)) {
            draft = true;
        }
        String peppolId = JwtUtil.getPeppolId(jwt);
        return documentService.update(peppolId, id, ublXml, draft, schedule, jwt.getTokenValue());
    }

    @PutMapping("{id}/send")
    @Operation(summary = "Send or schedule document", description = "Marks a document for transmission through Peppol immediately or at a scheduled time.")
    public DocumentDto send(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam(required = false) Instant schedule) {
        if (!JwtUtil.isPeppolActive(jwt)) {
            throw new PeppolException("Peppol ID is not active");
        }
        String peppolId = JwtUtil.getPeppolId(jwt);
        return documentService.send(peppolId, id, schedule, jwt.getTokenValue());
    }

    @PutMapping("{id}/read")
    @Operation(summary = "Mark document as read", description = "Updates the document workflow state to indicate it has been read by the current company.")
    public DocumentDto read(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return documentService.read(peppolId, id);
    }

    @PutMapping("{id}/paid")
    @Operation(summary = "Mark document as paid", description = "Updates the document workflow state to indicate it has been paid.")
    public DocumentDto paid(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return documentService.paid(peppolId, id);
    }

    @DeleteMapping("{id}")
    @Operation(summary = "Delete document", description = "Deletes a stored document owned by the authenticated company.")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        documentService.delete(peppolId, id);
    }

    @GetMapping("{id}/pdf")
    @Operation(summary = "Render document as PDF", description = "Generates a PDF view of the stored UBL document for preview or download.")
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

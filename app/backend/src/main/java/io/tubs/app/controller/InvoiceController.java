package io.tubs.app.controller;

import io.tubs.app.dto.InvoiceDraftDto;
import io.tubs.app.service.InvoiceDraftService;
import io.tubs.app.service.ValidationService;
import io.tubs.app.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/invoice")
public class InvoiceController {
    
    private final ValidationService validationService;
    private final InvoiceDraftService invoiceDraftService;

    @PostMapping("/validate")
    public ResponseEntity<?> validate(@RequestBody Map<String, Object> map) {
        String xml = (String) map.get("xml");
        if (xml == null || xml.isBlank()) {
            return ResponseEntity.badRequest().body("Missing XML content");
        }
        Map<String, Object> response = validationService.validateXml(xml);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/draft")
    public List<InvoiceDraftDto> getParties(@AuthenticationPrincipal Jwt jwt) {
        String companyNumber = JwtUtil.getCompanyNumber(jwt);
        return invoiceDraftService.findByCompanyNumber(companyNumber);
    }

    @PutMapping("/draft/{id}")
    public InvoiceDraftDto updateInvoiceDraft(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody InvoiceDraftDto invoiceDraftDto) {
        String companyNumber = JwtUtil.getCompanyNumber(jwt);
        return invoiceDraftService.updateDraft(companyNumber, id, invoiceDraftDto);
    }

    @PostMapping("/draft")
    public InvoiceDraftDto createInvoiceDraft(@AuthenticationPrincipal Jwt jwt, @RequestBody InvoiceDraftDto invoiceDraftDto) {
        String companyNumber = JwtUtil.getCompanyNumber(jwt);
        return invoiceDraftService.createDraft(companyNumber, invoiceDraftDto);
    }

    @DeleteMapping("/draft/{id}")
    public void deleteInvoiceDraft(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        String companyNumber = JwtUtil.getCompanyNumber(jwt);
        invoiceDraftService.deleteDraft(id, companyNumber);
    }
}

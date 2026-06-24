package org.letspeppol.app.controller;

import lombok.RequiredArgsConstructor;
import org.letspeppol.app.dto.VatReasonSelectionDto;
import org.letspeppol.app.service.InvoiceVatReasonSelectionService;
import org.letspeppol.app.util.JwtUtil;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sapi/invoice-vat-reason-selection")
public class InvoiceVatReasonSelectionController {

    private final InvoiceVatReasonSelectionService invoiceVatReasonSelectionService;

    @PostMapping
    public void create(@AuthenticationPrincipal Jwt jwt, @RequestBody List<VatReasonSelectionDto> selections) {
        invoiceVatReasonSelectionService.recordSelections(JwtUtil.getPeppolId(jwt), selections);
    }
}

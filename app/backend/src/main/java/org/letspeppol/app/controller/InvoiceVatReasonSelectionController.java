package org.letspeppol.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "App Invoice VAT Feedback", description = "Records the VAT-reason choices made while composing invoices so rules and UX can be evaluated.")
@SecurityRequirement(name = "oauth2", scopes = "openid")
public class InvoiceVatReasonSelectionController {

    private final InvoiceVatReasonSelectionService invoiceVatReasonSelectionService;

    @PostMapping
    @Operation(summary = "Record VAT-reason selections", description = "Stores a batch of VAT-reason selections for the authenticated company. This telemetry contains rule choices, not invoice contents.")
    public void create(@AuthenticationPrincipal Jwt jwt, @RequestBody List<VatReasonSelectionDto> selections) {
        invoiceVatReasonSelectionService.recordSelections(JwtUtil.getPeppolId(jwt), selections);
    }
}

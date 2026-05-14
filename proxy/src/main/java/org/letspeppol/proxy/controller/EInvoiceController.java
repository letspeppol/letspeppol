package org.letspeppol.proxy.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.letspeppol.proxy.dto.UblDocumentDto;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.service.UblDocumentService;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/e-invoice")
@Tag(name = "Proxy E-Invoice", description = "Inbound webhook-style endpoints reserved for receiving documents from external e-invoicing providers.")
public class EInvoiceController {

    private final UblDocumentService ublDocumentService;

    @PostMapping()
    @Operation(summary = "Receive external invoice document", description = "Accepts an inbound UBL document payload from an external e-invoicing provider for later proxy processing.")
    public void createAsReceived(@RequestBody UblDocumentDto ublDocumentDto) {
//        ublDocumentService.createAsReceived(ublDocumentDto, AccessPoint.E_INVOICE, "AP-uuid");
    }

}

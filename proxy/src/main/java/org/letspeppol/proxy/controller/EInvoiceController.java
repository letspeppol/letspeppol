package org.letspeppol.proxy.controller;

import lombok.RequiredArgsConstructor;
import org.letspeppol.proxy.dto.UblDocumentDto;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.service.UblDocumentService;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/e-invoice")
public class EInvoiceController {

    private final UblDocumentService ublDocumentService;

    @PostMapping()
    public void createAsReceived(@RequestBody UblDocumentDto ublDocumentDto) {
//        ublDocumentService.createAsReceived(ublDocumentDto, AccessPoint.E_INVOICE, "AP-uuid");
    }

}

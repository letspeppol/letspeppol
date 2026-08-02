package org.letspeppol.app.service;

import lombok.RequiredArgsConstructor;
import org.letspeppol.app.dto.VatReasonSelectionDto;
import org.letspeppol.app.exception.AppErrorCodes;
import org.letspeppol.app.exception.NotFoundException;
import org.letspeppol.app.exception.SecurityException;
import org.letspeppol.app.model.Document;
import org.letspeppol.app.model.InvoiceVatReasonSelection;
import org.letspeppol.app.repository.DocumentRepository;
import org.letspeppol.app.repository.InvoiceVatReasonSelectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class InvoiceVatReasonSelectionService {

    private final InvoiceVatReasonSelectionRepository invoiceVatReasonSelectionRepository;
    private final DocumentRepository documentRepository;

    public void recordSelections(String peppolId, List<VatReasonSelectionDto> selections) {
        if (selections == null || selections.isEmpty()) {
            return;
        }

        List<InvoiceVatReasonSelection> entities = selections.stream()
                .map(selection -> toEntity(peppolId, selection))
                .filter(item -> item != null)
                .toList();

        if (entities.isEmpty()) {
            return;
        }

        invoiceVatReasonSelectionRepository.saveAll(entities);
    }

    private InvoiceVatReasonSelection toEntity(String peppolId, VatReasonSelectionDto selection) {
        if (selection == null) {
            return null;
        }

        String selectedTaxCategoryId = selection.selectedTaxCategoryId() == null ? "" : selection.selectedTaxCategoryId().trim();
        String writtenReason = selection.writtenReason() == null ? "" : selection.writtenReason().trim();
        if (selectedTaxCategoryId.isEmpty() || writtenReason.isEmpty()) {
            return null;
        }

        return new InvoiceVatReasonSelection(
                resolveDocument(peppolId, selection.documentId()),
                selectedTaxCategoryId,
                writtenReason,
                selection.duringDraft()
        );
    }

    private Document resolveDocument(String peppolId, UUID documentId) {
        if (documentId == null) {
            return null;
        }

        Document document = documentRepository.findById(documentId).orElseThrow(() -> new NotFoundException("Document does not exist"));
        if (!peppolId.equals(document.getOwnerPeppolId())) {
            throw new SecurityException(AppErrorCodes.PEPPOL_ID_MISMATCH);
        }
        return document;
    }
}

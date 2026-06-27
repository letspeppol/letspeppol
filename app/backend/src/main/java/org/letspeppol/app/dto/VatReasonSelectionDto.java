package org.letspeppol.app.dto;

import java.util.UUID;

public record VatReasonSelectionDto(
        UUID documentId,
        String selectedTaxCategoryId,
        String writtenReason,
        boolean duringDraft
) {
}

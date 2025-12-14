package org.letspeppol.app.dto;

import lombok.Data;
import org.letspeppol.app.model.DocumentDirection;
import org.letspeppol.app.model.DocumentType;

@Data
public class DocumentFilter {

    private String ownerPeppolId;
    private DocumentType type;
    private DocumentDirection direction;
    private String partnerName;
    private String invoiceReference;
    private Boolean paid;
    private Boolean read;
    private Boolean draft;
}


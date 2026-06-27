package org.letspeppol.app.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "invoice_vat_reason_selection")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceVatReasonSelection extends GenericEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_uid")
    private Document document;

    private String selectedTaxCategoryId;

    private String writtenReason;

    private boolean duringDraft;

    public InvoiceVatReasonSelection(Document document, String selectedTaxCategoryId, String writtenReason, boolean duringDraft) {
        this.document = document;
        this.selectedTaxCategoryId = selectedTaxCategoryId;
        this.writtenReason = writtenReason;
        this.duringDraft = duringDraft;
    }
}

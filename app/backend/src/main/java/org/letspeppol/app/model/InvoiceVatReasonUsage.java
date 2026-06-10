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
@Table(name = "invoice_vat_reason_usage")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceVatReasonUsage extends GenericEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_uid", nullable = false)
    private Document document;

    private String selectedTaxCategoryId;

    private String writtenReason;

    public InvoiceVatReasonUsage(Document document, String selectedTaxCategoryId, String writtenReason) {
        this.document = document;
        this.selectedTaxCategoryId = selectedTaxCategoryId;
        this.writtenReason = writtenReason;
    }
}

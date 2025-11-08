package io.tubs.app.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Setter
@Getter
@NoArgsConstructor
public class InvoiceDraft extends GenericEntity {

    private String docType;
    private String docId;
    private String counterPartyName;
    private String createdAt;
    private String dueDate;
    private BigDecimal amount;
    @Lob
    private String xml;

    @ManyToOne
    @JoinColumn(name = "company_id", referencedColumnName = "id", foreignKey = @ForeignKey(name = "fk_partner_company"))
    private Company company;

    public InvoiceDraft(String docType, String docId, String counterPartyName, String createdAt, String dueDate, BigDecimal amount, String xml) {
        this.docType = docType;
        this.docId = docId;
        this.counterPartyName = counterPartyName;
        this.createdAt = createdAt;
        this.dueDate = dueDate;
        this.amount = amount;
        this.xml = xml;
    }
}

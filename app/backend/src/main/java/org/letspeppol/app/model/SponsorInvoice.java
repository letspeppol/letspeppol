package org.letspeppol.app.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import static org.hibernate.type.SqlTypes.VARCHAR;

@Entity
@Table(name = "sponsor_invoice")
@Getter
@Setter
@NoArgsConstructor
public class SponsorInvoice extends GenericEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", referencedColumnName = "id", foreignKey = @ForeignKey(name = "fk_sponsor_invoice_company"))
    private Company company;

    @Column(nullable = false)
    private Instant sponsoredOn;

    @Column(nullable = false)
    private BigDecimal amount;

    @Convert(disableConversion = true)
    @JdbcTypeCode(VARCHAR)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String message;

    @Column(nullable = false, unique = true)
    private String invoiceId;

    public SponsorInvoice(Company company, Instant sponsoredOn, BigDecimal amount, Currency currency, String name, String message, String invoiceId) {
        this.company = company;
        this.sponsoredOn = sponsoredOn;
        this.amount = amount;
        this.currency = currency;
        this.name = name;
        this.message = message;
        this.invoiceId = invoiceId;
    }
}

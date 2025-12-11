package org.letspeppol.app.model;

import jakarta.persistence.*;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.*;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import static org.hibernate.type.SqlTypes.VARCHAR;

@Entity
@Table(name = "document")
@Getter
@Setter
@NoArgsConstructor
public class Document {

    ///EXTERNAL INFORMATION

    @Id
    @UuidGenerator //TODO : look into (style = UuidGenerator.Style.TIME) // time-based UUID (v1-style)
    private UUID id; //Unique identifier used for communication with proxy

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private DocumentDirection direction; //Used for filtering when downloaded by retrieving app

    private String ownerPeppolId; //Sender or receiver, depending on direction

    private String partnerPeppolId; //Receiver or sender, depending on direction

    private Instant proxyOn; //Is the createdOn at Proxy

    private Instant scheduledOn; //Useful for keeping track when it is scheduled to be processed, will be set when throttling is active to a moment in the future

    private Instant processedOn; //Useful for keeping track when it is finished by Peppol AP

    private String processedStatus; //Useful for feedback from Peppol AP

    @Lob
    private String ubl; //Can be left empty once processed as owner owns the data, i.e. no-archive is enabled and downloadCount > 1, the other field are sufficient to keep the proxy fully operational

    ///INTERNAL INFORMATION

    @ManyToOne
    @JoinColumn(name = "company_id", referencedColumnName = "id", foreignKey = @ForeignKey(name = "fk_document_company"))
    private Company company; //Owner of this database record, should match ownerPeppolId by Peppol ID

    @CreationTimestamp(source = SourceType.DB)
    private Instant createdOn; //Useful for keeping track of creations, different from createdOn at Proxy

    private Instant draftedOn; //Updating last timestamp draft was still draft, null is no longer draft

    private Instant readOn; //Useful for keeping track of read status, flagged by user

    private Instant paidOn; //Useful for keeping track of paid status, flagged by user

    //TODO : private Instant accountantOn; //Useful for keeping track of accountant status, action executed by user, send to company.accountantEmail or null when not send yet/successful

    ///UBL INFORMATION

    private String partnerName; //Name of partner (may or may not be a real registered partner), belongs to the partnerPeppolId, used for overview

    private String invoiceReference; //Invoice number, used for overview

    private String buyerReference; //Buyer reference, used for searching

    private String orderReference; //Purchase order reference, used for searching

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private DocumentType type; //Type of document will be used to know if money needs to be paid or received, used for overview

    @Convert(disableConversion = true)  //Not using JPA converter
    @JdbcTypeCode(VARCHAR)
    private Currency currency; //Currency can be foreign, used for overview

    private BigDecimal amount; //Total payable amount, used for overview

    private Instant issueDate; //Issue date on document, used for overview

    private Instant dueDate; //Due date on document, used for overview

    private String paymentTerms; //When there is no due date, the payment terms are used for overview

    public Document(
            UUID id,
            DocumentDirection direction,
            String ownerPeppolId,
            String partnerPeppolId,
            Instant proxyOn,
            Instant scheduledOn,
            Instant processedOn,
            String processedStatus,
            String ubl,
//            Company company,
//            Instant createdOn,
            Instant draftedOn,
            Instant readOn,
            Instant paidOn,
            String partnerName,
            String invoiceReference,
            String buyerReference,
            String orderReference,
            DocumentType type,
            Currency currency,
            BigDecimal amount,
            Instant issueDate,
            Instant dueDate,
            String paymentTerms) {
        this.id              = id;
        this.direction       = direction;
        this.ownerPeppolId   = ownerPeppolId;
        this.partnerPeppolId = partnerPeppolId;
        this.proxyOn         = proxyOn;
        this.scheduledOn     = scheduledOn;
        this.processedOn     = processedOn;
        this.processedStatus = processedStatus;
        this.ubl             = ubl;
//        this.company         = company;
//        this.createdOn       = createdOn;
        this.draftedOn       = draftedOn;
        this.readOn          = readOn;
        this.paidOn          = paidOn;
        this.partnerName     = partnerName;
        this.invoiceReference= invoiceReference;
        this.buyerReference  = buyerReference;
        this.orderReference  = orderReference;
        this.type            = type;
        this.currency        = currency;
        this.amount          = amount;
        this.issueDate       = issueDate;
        this.dueDate         = dueDate;
        this.paymentTerms    = paymentTerms;
    }
}



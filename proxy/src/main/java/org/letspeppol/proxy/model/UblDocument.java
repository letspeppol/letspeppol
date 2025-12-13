package org.letspeppol.proxy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.*;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
public class UblDocument {

    ///EXTERNAL INFORMATION

    @Id
    private UUID id; //Unique identifier used for retrieving app and storage in database

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false)
    private DocumentDirection direction; //Used for filtering when downloaded by retrieving app

    @Column(nullable = false)
    private String ownerPeppolId; //Sender or receiver, depending on direction

    @Column(nullable = false)
    private String partnerPeppolId; //Receiver or sender, depending on direction

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdOn; //Useful for retrieving app that wants to download already downloaded ubl documents, is the proxyOn at App

    private Instant scheduledOn; //Useful for keeping track when it is scheduled to be processed, will be set when throttling is active to a moment in the future

    private Instant processedOn; //Useful for keeping track when it is finished by Peppol AP

//    private Instant receivedOn; //Useful for keeping track when it is received by Peppol AP of the receiver (and thus status is OK)
    //TODO : should we store full information about the status ?
//    @JdbcTypeCode(SqlTypes.JSON)
//    @Column(columnDefinition = "jsonb")
//    private Map<String, Object> processedStatus;
    private String processedStatus; //Useful for feedback from Peppol AP

    @Lob
    private String ubl; //Can be left empty once processed as owner owns the data, i.e. no-archive is enabled and downloadCount > 1, the other field are sufficient to keep the proxy fully operational

    ///INTERNAL INFORMATION

    @Column(nullable = false)
    private String hash; //Stores the hash of the ubl to validate for duplicates as the ubl could be empty, i.e. when already stored this ublDocument within certain time-frame, this could be a duplication problem and will not process it again

    @Column(nullable = false)
    private Integer downloadCount; //Starts at 0 and will increase for each validated download, used for monitoring proxy health, can be -1 to flag to empty ubl once send via AP

    @UpdateTimestamp
    private Instant updatedOn; //Starts at null and will be updated for each download to retrieving app or upload to Peppol AP, used for monitoring proxy health

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private AccessPoint accessPoint; //Can be null for outgoing ubl document when not yet send via Peppol AP

    private String accessPointId; //Could be a non-unique value when the same docUuid is used for sender and receiver, can be null for outgoing ubl document when not yet send via Peppol AP

}

package org.letspeppol.proxy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.UpdateTimestamp;
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
    private Direction direction; //Used for filtering when downloaded by retrieving app

    private String ownerPeppolId; //Sender or receiver, depending on direction

    private String partnerPeppolId; //Receiver or sender, depending on direction

    @CreationTimestamp(source = SourceType.DB)
    private Instant createdOn; //Useful for retrieving app that wants to download already downloaded ubl documents

    private Instant scheduledOn; //Useful for keeping track when it is scheduled to be processed, will be set when throttling is active to a moment in the future

    private Instant processedOn; //Useful for keeping track when it is finished by Peppol AP

    private String processedStatus; //Useful for feedback from Peppol AP

    @Lob
    private String ubl; //Can be left empty once processed as owner owns the data, i.e. no-archive is enabled and downloadCount > 1, the other field are sufficient to keep the proxy fully operational

    ///INTERNAL INFORMATION

    private String hash; //Stores the hash of the ubl to validate for duplicates as the ubl could be empty, i.e. when already stored this ublDocument within certain time-frame, this could be a duplication problem and will not process it again

    private Integer downloadCount; //Starts at 0 and will increase for each validated download, used for monitoring proxy health, can be -1 to flag to empty ubl once send via AP

    @UpdateTimestamp(source = SourceType.DB)
    private Instant updatedOn; //Starts at null and will be updated for each download to retrieving app or upload to Peppol AP, used for monitoring proxy health

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private AccessPoint accessPoint; //Can be null for outgoing ubl document when not yet send via Peppol AP

    private String accessPointId; //Could be a non-unique value when the same docUuid is used for sender and receiver, can be null for outgoing ubl document when not yet send via Peppol AP

}

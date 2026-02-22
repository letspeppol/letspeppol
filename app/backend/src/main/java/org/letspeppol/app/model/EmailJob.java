package org.letspeppol.app.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Builder
@Entity
@Table(name = "email_job")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class EmailJob extends GenericEntity {

    public enum Status {
        PENDING,
        SENT,
        FAILED
    }

    public enum Template {
        DOCUMENT_NOTIFICATION,
        ACCOUNTANT_CUSTOMER_LINK,
        GENERIC
    }

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.PENDING;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Template template = Template.DOCUMENT_NOTIFICATION;

    private String toAddress;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    private Instant sentAt;

}

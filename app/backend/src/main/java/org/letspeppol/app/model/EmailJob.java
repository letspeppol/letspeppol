package org.letspeppol.app.model;

import jakarta.persistence.*;
import lombok.*;

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
        DOCUMENT_NOTIFICATION
    }

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    @Enumerated(EnumType.STRING)
    private Template template = Template.DOCUMENT_NOTIFICATION;

    private String toAddress;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    private Instant sentAt;

}

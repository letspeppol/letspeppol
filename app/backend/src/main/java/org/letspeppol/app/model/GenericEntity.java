package org.letspeppol.app.model;

import jakarta.persistence.*;
import lombok.Getter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Getter
@MappedSuperclass
public class GenericEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Long id;

    @CreationTimestamp
    protected Instant createdOn;

    @UpdateTimestamp
    protected Instant lastUpdatedOn;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    protected boolean active = true;

}

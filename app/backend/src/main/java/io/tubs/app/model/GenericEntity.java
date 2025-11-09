package io.tubs.app.model;

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

    @CreationTimestamp(source = SourceType.DB)
    protected Instant createdOn;

    @UpdateTimestamp(source = SourceType.DB)
    protected Instant lastUpdatedOn;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    protected boolean active = true;

}

package org.letspeppol.kyc.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.letspeppol.kyc.model.kbo.Company;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "\"user\"", indexes = {
        @Index(name = "idx_company_email", columnList = "company_id,email"),
        @Index(name = "idx_user_external_id", columnList = "external_id")
})
@Getter
@Setter
@Builder
@AllArgsConstructor
public class User {

    public User() {
        this.externalId = UUID.randomUUID();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Builder.Default
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(nullable = false)
    private boolean identityVerified = false;
    private Instant identityVerifiedAt;

    @Column(unique = true, nullable = false)
    private UUID externalId;

}

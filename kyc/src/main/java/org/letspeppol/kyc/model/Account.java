package org.letspeppol.kyc.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.letspeppol.kyc.model.kbo.Company;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "account")
@Getter
@Setter
@Builder
@AllArgsConstructor
public class Account {

    public Account() {
        this.externalId = UUID.randomUUID();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("lastUsed DESC")
    private List<Ownership> ownerships = new ArrayList<>();

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Builder.Default
    @Column(nullable = false)
    private Instant createdOn = Instant.now();

    @Builder.Default
    @Column(nullable = false)
    private boolean identityVerified = false;
    private Instant identityVerifiedOn;

    @Builder.Default
    @Column(unique = true, nullable = false)
    private UUID externalId = UUID.randomUUID(); //Is an ID that is allowed to be exposed externally

}

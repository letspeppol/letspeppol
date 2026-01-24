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
@Table(name = "account", indexes = {
        @Index(name = "idx_account_company_email", columnList = "company_id,email"),
        @Index(name = "idx_account_external_id", columnList = "external_id")
})
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

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private AccountType type;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

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

    @Column(unique = true, nullable = false)
    private UUID externalId;

    @Builder.Default
    @ManyToMany
    @JoinTable(
            name = "account_link",
            joinColumns = @JoinColumn(name = "account_id"),
            inverseJoinColumns = @JoinColumn(name = "linked_account_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_account_link_pair", columnNames = {"account_id", "linked_account_id"})
    )
    private List<Account> linkedAccounts = new ArrayList<>();

}

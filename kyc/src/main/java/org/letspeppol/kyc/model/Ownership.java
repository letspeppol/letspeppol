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

@Entity
@Table(name = "ownership")
@Getter
@Setter
@Builder
@AllArgsConstructor
public class Ownership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false)
    private AccountType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Builder.Default
    @Column(nullable = false)
    private Instant createdOn = Instant.now();

    @Builder.Default
    @Column(nullable = false)
    private Instant lastUsed = Instant.now();

    public Ownership(Account account, AccountType type, Company company) {
        this.account = account;
        this.type = type;
        this.company = company;
    }

}

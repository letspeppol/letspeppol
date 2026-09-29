package org.letspeppol.kyc.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import org.letspeppol.kyc.model.kbo.Company;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "account")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Account {

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

    @Column(nullable = true, length = 100)
    private String passwordHash;

    @Builder.Default
    @Column(nullable = false)
    private Instant createdOn = Instant.now();

    @Builder.Default
    @Column(nullable = false)
    private boolean verified = false;
    private Instant verifiedOn;

    @Column(name = "totp_secret")
    private String totpSecret;

    @Builder.Default
    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled = false;

    @Column(name = "totp_recovery_codes")
    private String totpRecoveryCodes;

    @Builder.Default
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "account_permission", joinColumns = @JoinColumn(name = "account_id"))
    @Column(name = "permission", nullable = false)
    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private Set<Permission> permissions = new HashSet<>();

    @Builder.Default
    @Column(unique = true, nullable = false)
    private UUID externalId = UUID.randomUUID(); //Is an ID that is allowed to be exposed externally

}

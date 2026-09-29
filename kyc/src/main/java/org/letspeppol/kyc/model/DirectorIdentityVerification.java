package org.letspeppol.kyc.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;
import org.letspeppol.kyc.model.kbo.Director;

import java.time.Instant;

@Entity
@Table(name = "director_identity_verification")
@Getter
@Setter
@NoArgsConstructor
public class DirectorIdentityVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "director_id", nullable = false)
    private Director director;

    @Column(nullable = false)
    private String directorNameSnapshot;

    @Column(nullable = false)
    private String certificateSubject;

    @Column(nullable = false)
    private String certificateSerial;

    @Column(nullable = false)
    private String signatureAlgorithm;

    @Column(nullable = false)
    private String dataHash;

    @Lob
    @Column(nullable = false)
    private String certificate;

    @Lob
    @Column(nullable = false)
    private String signature;

    @Column(nullable = false)
    private Instant createdOn = Instant.now();

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private AccountType requestedType;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false)
    private ReviewStatus reviewStatus = ReviewStatus.NOT_REQUIRED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private Account reviewedBy;

    private Instant reviewedOn;

    public DirectorIdentityVerification(Account account, Director director, AccountType requestedType, String directorNameSnapshot,
                                        String certificateSubject, String certificateSerial,
                                        String signatureAlgorithm, String dataHash, String certificate, String signature) {
        this.account = account;
        this.requestedType = requestedType;
        this.director = director;
        this.directorNameSnapshot = directorNameSnapshot;
        this.certificateSubject = certificateSubject;
        this.certificateSerial = certificateSerial;
        this.signatureAlgorithm = signatureAlgorithm;
        this.dataHash = dataHash;
        this.certificate = certificate;
        this.signature = signature;
    }
}


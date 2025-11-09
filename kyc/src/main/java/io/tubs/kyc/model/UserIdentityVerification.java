package io.tubs.kyc.model;

import io.tubs.kyc.model.kbo.Director;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "user_identity_verification", indexes = {
        @Index(name = "idx_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
public class UserIdentityVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

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
    private Instant createdAt = Instant.now();

    public UserIdentityVerification(User user, Director director, String directorNameSnapshot,
                                    String certificateSubject, String certificateSerial,
                                    String signatureAlgorithm, String dataHash, String certificate, String signature) {
        this.user = user;
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


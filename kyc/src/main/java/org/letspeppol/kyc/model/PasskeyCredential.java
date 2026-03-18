package org.letspeppol.kyc.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "passkey_credential", indexes = {
        @Index(name = "idx_passkey_credential_account_id", columnList = "account_id")
})
@Getter
@Setter
@Builder
@AllArgsConstructor
public class PasskeyCredential {

    public PasskeyCredential() {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false, unique = true)
    private byte[] credentialId;

    @Column(nullable = false)
    private byte[] publicKeyCose;

    @Column(nullable = false)
    @Builder.Default
    private long signCount = 0;

    private String transports;

    @Column(nullable = false)
    private String displayName;

    private UUID aaguid;

    @Column(nullable = false)
    @Builder.Default
    private boolean discoverable = true;

    @Builder.Default
    @Column(nullable = false)
    private Instant createdOn = Instant.now();

    private Instant lastUsedOn;
}

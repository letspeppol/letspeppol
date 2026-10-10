package org.letspeppol.kyc.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "ownership_invitation")
@Getter
@Setter
@NoArgsConstructor
public class OwnershipInvitation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ownership_id", nullable = false, unique = true)
    private Ownership ownership;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_account_id")
    private Account invitedBy;

    @Column(nullable = false)
    private String invitedName;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(nullable = false)
    private Instant expiresOn;

    @Column(nullable = false)
    private Instant createdOn;

    public OwnershipInvitation(Ownership ownership, Account invitedBy, String invitedName, String token, Instant expiresOn) {
        this.ownership = ownership;
        this.invitedBy = invitedBy;
        this.invitedName = invitedName;
        this.token = token;
        this.expiresOn = expiresOn;
        this.createdOn = Instant.now();
    }

    public boolean isExpired() {
        return expiresOn.isBefore(Instant.now());
    }
}

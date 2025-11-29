package org.letspeppol.kyc.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "password_reset_token", indexes = {
        @Index(name = "idx_password_reset_token_token", columnList = "token", unique = true),
        @Index(name = "idx_password_reset_token_account_id", columnList = "account_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @Column(nullable = false, length = 200)
    private String token;

    @Column(nullable = false)
    private Instant expiresOn;

    @Column(nullable = false)
    @Builder.Default
    private Instant createdOn = Instant.now();

    private Instant usedOn;

    public boolean isExpired() { return expiresOn.isBefore(Instant.now()); }
    public boolean isUsed() { return usedOn != null; }
    public void markUsed() { this.usedOn = Instant.now(); }
}


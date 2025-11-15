package org.letspeppol.kyc.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "password_reset_token", indexes = {
        @Index(name = "idx_password_reset_token_token", columnList = "token", unique = true),
        @Index(name = "idx_password_reset_token_user", columnList = "user_id")
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
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 200)
    private String token;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant usedAt;

    public boolean isExpired() { return expiresAt.isBefore(Instant.now()); }
    public boolean isUsed() { return usedAt != null; }
    public void markUsed() { this.usedAt = Instant.now(); }
}


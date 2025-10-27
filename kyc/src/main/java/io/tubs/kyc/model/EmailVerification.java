package io.tubs.kyc.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "email_verification", indexes = {
        @Index(name = "uk_token", columnList = "token", unique = true),
        @Index(name = "idx_email_company", columnList = "email,companyNumber")
})
@Getter
@Setter
@NoArgsConstructor
public class EmailVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @NotBlank
    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String companyNumber;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(nullable = false)
    private boolean verified;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    public EmailVerification(String email, String companyNumber, String token, Instant expiresAt) {
        this.email = email;
        this.companyNumber = companyNumber;
        this.token = token;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
        this.verified = false;
    }
}

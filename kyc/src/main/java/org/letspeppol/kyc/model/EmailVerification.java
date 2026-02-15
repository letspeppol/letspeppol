package org.letspeppol.kyc.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.time.Instant;

@Entity
@Table(name = "email_verification", indexes = {
        @Index(name = "uk_token", columnList = "token", unique = true),
        @Index(name = "idx_email_company", columnList = "email,peppolId")
})
@Getter
@Setter
@NoArgsConstructor
public class EmailVerification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "requester_account_id")
    private Account requester;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    private AccountType type;

    @NotNull
    @NotBlank
    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String peppolId;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(nullable = false)
    private boolean verified;

    @Column(nullable = false)
    private Instant expiresOn;

    @Column(nullable = false)
    private Instant createdOn;

    public EmailVerification(Account requester, AccountType type, String email, String peppolId, String token, Instant expiresOn) {
        this.requester = requester;
        this.type = type;
        this.email = email;
        this.peppolId = peppolId;
        this.token = token;
        this.expiresOn = expiresOn;
        this.createdOn = Instant.now();
        this.verified = false;
    }
}

package org.letspeppol.app.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accountant_company")
@Getter
@Setter
@NoArgsConstructor
public class AccountantCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private UUID accountantExternalId;
    private String customerPeppolId;
    private String customerEmail;
    private String customerName;
    private Instant invitedOn;
    private Instant verifiedOn;
    private Instant lastDownloadCreatedOn;
    private Instant lastDownloadIssuedOn;

    public AccountantCompany(UUID accountantExternalId, String customerPeppolId, String customerEmail, String customerName) {
        this.accountantExternalId = accountantExternalId;
        this.customerPeppolId = customerPeppolId;
        this.customerEmail = customerEmail;
        this.customerName = customerName;
    }
}

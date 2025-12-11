package org.letspeppol.app.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "company", indexes = {
        @Index(name = "uk_company_number", columnList = "peppolId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class Company extends GenericEntity{

    @Column(nullable = false, unique = true)
    private String peppolId;

    private String vatNumber;

    @Column(nullable = false)
    private String name;

    private String subscriber; // Director name
    private String subscriberEmail;

    private String paymentTerms;
    private String iban;
    private String paymentAccountName;

    //TODO : private boolean noArchive; //Setting by user that data should not be stored once processed (user is absolute owner & responsible)
    //TODO : private Instant lastDocumentSyncAt; //Rate limit the proxy polling
    //TODO : private String accountantEmail; //Email of accounting system or accountant, flaggable by user what invoices should be send to accountant

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "registered_office_id", referencedColumnName = "id", foreignKey = @ForeignKey(name = "fk_company_registered_office"))
    private Address registeredOffice;

    public Company(String peppolId, String vatNumber, String name, String subscriber, String subscriberEmail,
                   String city, String postalCode, String street, String countryCode) {
        this.peppolId = peppolId;
        this.vatNumber = vatNumber;
        this.name = name;
        this.subscriber = subscriber;
        this.subscriberEmail = subscriberEmail;
        this.registeredOffice = new Address(city, postalCode, street, countryCode);
    }
}

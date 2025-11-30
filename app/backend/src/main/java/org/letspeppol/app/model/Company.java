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
    private boolean registeredOnPeppol = false;

    private String paymentTerms;
    private String iban;
    private String paymentAccountName;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "registered_office_id", referencedColumnName = "id", foreignKey = @ForeignKey(name = "fk_company_registered_office"))
    private Address registeredOffice;

    public Company(String peppolId, String vatNumber, String name, String subscriber, String subscriberEmail,
                   String city, String postalCode, String street, String houseNumber, String countryCode) {
        this.peppolId = peppolId;
        this.vatNumber = vatNumber;
        this.name = name;
        this.subscriber = subscriber;
        this.subscriberEmail = subscriberEmail;
        this.registeredOffice = new Address(street, houseNumber, city, postalCode, countryCode);
        this.registeredOnPeppol = true;
    }
}

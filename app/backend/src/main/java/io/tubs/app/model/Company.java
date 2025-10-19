package io.tubs.app.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "companies", indexes = {
        @Index(name = "uk_company_number", columnList = "companyNumber", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class Company extends GenericEntity{

    @Column(nullable = false, unique = true)
    private String companyNumber;

    @Column(nullable = false)
    private String name;

    private String subscriber; // Director name
    private String subscriberEmail;
    private boolean registeredOnPeppol = false;

    private String paymentTerms;
    private String iban;
    private String paymentAccountName;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "registered_office_id", referencedColumnName = "id")
    private Address registeredOffice;

    public Company(String companyNumber, String name, String subscriber, String subscriberEmail,
                   String city, String postalCode, String street, String houseNumber) {
        this.companyNumber = companyNumber;
        this.name = name;
        this.subscriber = subscriber;
        this.subscriberEmail = subscriberEmail;
        this.registeredOffice = new Address(street, houseNumber, city, postalCode);
        this.registeredOnPeppol = true;
    }
}

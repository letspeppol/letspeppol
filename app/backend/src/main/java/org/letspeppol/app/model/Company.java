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
    private String lastInvoiceReference;

// TODO    private boolean noArchive; //Setting by user that data should not be stored once processed (user is absolute owner & responsible)
// TODO    private String accountant; //Either email or UUID of accounting system or accountant, flaggable by user what invoices should be sent to accountant
//CREATE SCHEMA IF NOT EXISTS app;
//    SET search_path = app;
//
//-- Company
//    ALTER TABLE company
//    ADD COLUMN no_archive boolean DEFAULT false NOT NULL,
//    ADD COLUMN accountant varchar(255);

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
        // TODO        this.noArchive = false;
        this.registeredOffice = new Address(city, postalCode, street, countryCode);
    }
}

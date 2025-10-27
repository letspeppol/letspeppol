package io.tubs.app.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "companies", indexes = {
        @Index(name = "uk_company_number", columnList = "companyNumber", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String companyNumber;

    @Column(nullable = false)
    private String name;

    private String subscriber; // Director name
    private String subscriberEmail;

    private String paymentTerms;
    private String iban;
    private String paymentAccountName;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "registered_office_id", referencedColumnName = "id")
    private Address registeredOffice;

    public Company(String companyNumber, String name, String street, String houseNumber, String city, String postalCode, String subscriber, String subscriberEmail) {
        this.companyNumber = companyNumber;
        this.name = name;
        this.subscriber = subscriber;
        this.subscriberEmail = subscriberEmail;
        this.registeredOffice = new Address(street, houseNumber, city, postalCode);
    }
}

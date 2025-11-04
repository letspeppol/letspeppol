package io.tubs.app.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "partners")
@Getter
@Setter
@NoArgsConstructor
public class Partner extends GenericEntity {

    private String companyNumber;
    private String name;
    private String email;
    private String peppolId;
    private Boolean customer;
    private Boolean supplier;

    private String paymentTerms;
    private String iban;
    private String paymentAccountName;

    @ManyToOne
    @JoinColumn(name = "company_id", referencedColumnName = "id", foreignKey = @ForeignKey(name = "fk_partner_company"))
    private Company company;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "registered_office_id", referencedColumnName = "id")
    private Address registeredOffice;

    public Partner(String companyNumber, String name, String email, String peppolId, Boolean customer, Boolean supplier,
                   String paymentTerms, String iban, String paymentAccountName,
                 String city, String postalCode, String street, String houseNumber) {
        this.companyNumber = companyNumber;
        this.name = name;
        this.email = email;
        this.peppolId = peppolId;
        this.customer = customer;
        this.supplier = supplier;
        this.paymentTerms = paymentTerms;
        this.iban = iban;
        this.paymentAccountName = paymentAccountName;
        this.registeredOffice = new Address(city, postalCode, street, houseNumber);
    }

}

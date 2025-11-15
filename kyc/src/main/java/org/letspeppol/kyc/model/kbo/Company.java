package org.letspeppol.kyc.model.kbo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "company", indexes = {
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

    private String city;

    private String postalCode;

    private String street;

    private String houseNumber;

    private boolean registeredOnPeppol = false;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Director> directors = new ArrayList<>();

    public Company(String companyNumber, String name, String city, String postalCode, String street, String houseNumber) {
        this.companyNumber = companyNumber;
        this.name = name;
        this.city = city;
        this.postalCode = postalCode;
        this.street = street;
        this.houseNumber = houseNumber;
        this.registeredOnPeppol = true;
    }
}

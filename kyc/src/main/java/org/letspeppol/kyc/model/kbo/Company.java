package org.letspeppol.kyc.model.kbo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "company")
@Getter
@Setter
@NoArgsConstructor
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = true, unique = true)
    private String peppolId;

    private String vatNumber;

    @Column(nullable = false)
    private String name;

    private String city;

    private String postalCode;

    private String street;

    private String businessUnit;

    private String iban;

    private boolean hasKboAddress = true;

    private boolean registeredOnPeppol = false;

    private boolean suspended = false;

    @OneToMany(mappedBy = "company", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Director> directors = new ArrayList<>();

    public Company(String peppolId, String vatNumber, String name) {
        this.peppolId = peppolId;
        this.vatNumber = vatNumber;
        this.name = name;
        this.registeredOnPeppol = false;
        this.hasKboAddress = false;
        this.suspended = false;
    }

    public void setAddress(String city, String postalCode, String street) {
        this.city = city;
        this.postalCode = postalCode;
        this.street = street;
    }

    public boolean isPeppolActive() {
        return !suspended && registeredOnPeppol;
    }
}

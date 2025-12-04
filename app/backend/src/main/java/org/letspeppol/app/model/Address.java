package org.letspeppol.app.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "address")
@Getter
@Setter
@NoArgsConstructor
public class Address extends GenericEntity {

    private String city;
    private String postalCode;
    private String street;
    private String countryCode;

    public Address(String city, String postalCode, String street, String countryCode) {
        this.city = city;
        this.postalCode = postalCode;
        this.street = street;
        this.countryCode = countryCode;
    }
}

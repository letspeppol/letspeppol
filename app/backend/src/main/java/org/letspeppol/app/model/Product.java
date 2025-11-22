package org.letspeppol.app.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
public class Product extends GenericEntity {

    private String name;
    private String description;
    private String reference;
    private String barcode;
    private BigDecimal costPrice;
    private BigDecimal salePrice;
    private BigDecimal taxPercentage;
    private BigDecimal stockQuantity;

    @ManyToOne
    @JoinColumn(name = "company_id", referencedColumnName = "id", foreignKey = @ForeignKey(name = "fk_product_company"))
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", referencedColumnName = "id", foreignKey = @ForeignKey(name = "fk_product_category"))
    private ProductCategory category;

}

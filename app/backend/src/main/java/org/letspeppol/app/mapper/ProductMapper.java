package org.letspeppol.app.mapper;

import org.letspeppol.app.dto.ProductDto;
import org.letspeppol.app.model.Product;

public class ProductMapper {

    public static ProductDto toDto(Product product) {
        return new ProductDto(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getReference(),
            product.getBarcode(),
            product.getCostPrice(),
            product.getSalePrice(),
            product.getTaxPercentage(),
            product.getCategory() != null ? product.getCategory().getId() : null
        );
    }
}

package org.letspeppol.app.mapper;

import org.letspeppol.app.dto.ProductCategoryDto;
import org.letspeppol.app.model.ProductCategory;

import java.util.List;
import java.util.stream.Collectors;

public class ProductCategoryMapper {

    public static ProductCategoryDto toDto(ProductCategory category) {
        return toDto(category, false);
    }

    public static ProductCategoryDto toDto(ProductCategory category, boolean deep) {
        if (category == null) return null;
        List<ProductCategoryDto> children = List.of();
        if (deep && category.getSubcategories() != null && !category.getSubcategories().isEmpty()) {
            children = category.getSubcategories().stream()
                    .map(c -> toDto(c, true))
                    .collect(Collectors.toList());
        }
        return new ProductCategoryDto(
                category.getId(),
                category.getName(),
                category.getColor(),
                category.getParent() != null ? category.getParent().getId() : null,
                children
        );
    }
}


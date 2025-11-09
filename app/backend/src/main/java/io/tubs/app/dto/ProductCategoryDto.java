package io.tubs.app.dto;

import java.util.List;

public record ProductCategoryDto(
        Long id,
        String name,
        String color,
        Long parentId,
        List<ProductCategoryDto> children
) {}


package io.tubs.app.dto;

import java.math.BigDecimal;

public record ProductDto(
        Long id,
        String name,
        String description,
        String reference,
        String barcode,
        BigDecimal costPrice,
        BigDecimal salePrice,
        BigDecimal taxPercentage,
        Long categoryId
) {}

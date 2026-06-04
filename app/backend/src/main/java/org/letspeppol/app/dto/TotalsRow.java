package org.letspeppol.app.dto;

import java.math.BigDecimal;

/** Flat projection from the native totals query; lifted into the nested {@link TotalsDto} by {@link #toDto()}. */
public record TotalsRow(
        BigDecimal totalPayableOpenInclVat,
        BigDecimal totalPayableOverdueInclVat,
        BigDecimal totalPayableThisYearInclVat,
        BigDecimal totalReceivableOpenInclVat,
        BigDecimal totalReceivableOverdueInclVat,
        BigDecimal totalReceivableThisYearInclVat,
        BigDecimal totalPayableOpenExclVat,
        BigDecimal totalPayableOverdueExclVat,
        BigDecimal totalPayableThisYearExclVat,
        BigDecimal totalReceivableOpenExclVat,
        BigDecimal totalReceivableOverdueExclVat,
        BigDecimal totalReceivableThisYearExclVat
) {

    public TotalsDto.DirectionTotals inclVat() {
        return new TotalsDto.DirectionTotals(
                totalPayableOpenInclVat,
                totalPayableOverdueInclVat,
                totalPayableThisYearInclVat,
                totalReceivableOpenInclVat,
                totalReceivableOverdueInclVat,
                totalReceivableThisYearInclVat);
    }

    public TotalsDto.DirectionTotals exclVat() {
        return new TotalsDto.DirectionTotals(
                totalPayableOpenExclVat,
                totalPayableOverdueExclVat,
                totalPayableThisYearExclVat,
                totalReceivableOpenExclVat,
                totalReceivableOverdueExclVat,
                totalReceivableThisYearExclVat);
    }

    public TotalsDto toDto() {
        return new TotalsDto(inclVat(), exclVat());
    }
}

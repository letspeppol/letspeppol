package org.letspeppol.app.dto;

import java.math.BigDecimal;

public record TotalsDto(DirectionTotals inclVat, DirectionTotals exclVat) {

    public record DirectionTotals(
            BigDecimal payableOpen,
            BigDecimal payableOverdue,
            BigDecimal payableThisYear,
            BigDecimal receivableOpen,
            BigDecimal receivableOverdue,
            BigDecimal receivableThisYear
    ) {}
}

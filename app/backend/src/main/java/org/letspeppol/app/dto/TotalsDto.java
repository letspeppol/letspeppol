package org.letspeppol.app.dto;

import java.math.BigDecimal;

public record TotalsDto(
        BigDecimal totalPayableOpen,
        BigDecimal totalPayableOverdue,
        BigDecimal totalPayableThisYear,
        BigDecimal totalReceivableOpen,
        BigDecimal totalReceivableOverdue,
        BigDecimal totalReceivableThisYear
) {}

package org.letspeppol.app.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record SponsorContributionDto(
        String name,
        String message,
        BigDecimal amount,
        String currency,
        Instant date
) {}

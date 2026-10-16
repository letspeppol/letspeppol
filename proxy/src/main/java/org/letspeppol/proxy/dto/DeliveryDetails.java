package org.letspeppol.proxy.dto;

import java.time.Instant;

public record DeliveryDetails(
        String partnerPeppolAccessPoint,
        String partnerPeppolMessageId,
        Instant partnerPeppolMessageOn
) {}

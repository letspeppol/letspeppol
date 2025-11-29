package org.letspeppol.proxy.dto.e_invoice;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record RegisterPeppolResponse(
    boolean registered,
    @JsonProperty("peppol_id") String peppolId,
    String status,
    @JsonProperty("registered_at") Instant registeredAt
) {}

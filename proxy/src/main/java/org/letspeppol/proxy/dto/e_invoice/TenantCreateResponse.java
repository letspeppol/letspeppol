package org.letspeppol.proxy.dto.e_invoice;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record TenantCreateResponse(
    String id,
    String name,
    String description,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("is_deleted") boolean isDeleted
) {}

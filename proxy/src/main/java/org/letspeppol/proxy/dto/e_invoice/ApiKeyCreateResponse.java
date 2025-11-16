package org.letspeppol.proxy.dto.e_invoice;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record ApiKeyCreateResponse(
    String id,
    @JsonProperty("tenant_id") String tenantId,
    String name,
    String description,
    String key,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("is_deleted") boolean isDeleted
) {}

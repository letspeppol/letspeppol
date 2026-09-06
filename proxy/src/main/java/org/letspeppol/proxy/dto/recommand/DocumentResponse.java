package org.letspeppol.proxy.dto.recommand;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentResponse(
        boolean success,
        JsonNode document
) {}

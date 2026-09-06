package org.letspeppol.proxy.dto.recommand;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SendDocumentResponse(
        boolean success,
        boolean sentOverPeppol,
        String id
) {}

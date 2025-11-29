package org.letspeppol.proxy.dto.e_invoice;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RegisterPeppolRequest(
        @JsonProperty("peppol_id") String peppolId,
        @JsonProperty("company_name") String companyName
) {}

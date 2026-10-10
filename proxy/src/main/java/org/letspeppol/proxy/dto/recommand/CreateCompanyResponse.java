package org.letspeppol.proxy.dto.recommand;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateCompanyResponse(
        boolean success,
        Company company,
        String verificationUrl
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Company(String id) {}
}

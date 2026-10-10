package org.letspeppol.proxy.dto.recommand;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VerifyCompanyResponse(boolean success) {}

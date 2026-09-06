package org.letspeppol.proxy.dto.recommand;

public record CreateCompanyRequest(
        String name,
        String address,
        String postalCode,
        String city,
        String country,
        String enterpriseNumberScheme,
        String enterpriseNumber,
        String vatNumber,
        boolean isSmpRecipient,
        boolean skipDefaultCompanySetup
) {}

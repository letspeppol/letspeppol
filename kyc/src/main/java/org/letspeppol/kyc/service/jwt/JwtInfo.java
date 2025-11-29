package org.letspeppol.kyc.service.jwt;

public record JwtInfo(
        String token,
        String peppolId,
        Boolean peppolActive,
        String uid
) {}
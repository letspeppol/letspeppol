package org.letspeppol.kyc.service.jwt;

import java.util.UUID;

public record JwtInfo(
        String token,
        String peppolId,
        Boolean peppolActive,
        UUID uid
) {}
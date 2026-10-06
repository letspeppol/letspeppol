package org.letspeppol.kyc.dto;

import java.time.Instant;

public record RevokedToken(
        String jti,
        Instant expiresAt
) {}

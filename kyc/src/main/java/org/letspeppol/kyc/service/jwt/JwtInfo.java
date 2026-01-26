package org.letspeppol.kyc.service.jwt;

import org.letspeppol.kyc.model.AccountType;

import java.util.UUID;

public record JwtInfo(
        String token,
        AccountType accountType,
        String peppolId,
        Boolean peppolActive,
        UUID uid
) {}
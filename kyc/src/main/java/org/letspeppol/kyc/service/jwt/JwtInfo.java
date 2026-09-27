package org.letspeppol.kyc.service.jwt;

import org.letspeppol.kyc.model.AccountType;

import java.util.UUID;

public record JwtInfo(
        AccountType accountType,
        String peppolId,
        Boolean peppolActive,
        UUID uid
) {}
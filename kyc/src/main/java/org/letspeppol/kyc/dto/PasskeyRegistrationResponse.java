package org.letspeppol.kyc.dto;

import java.util.List;

public record PasskeyRegistrationResponse(
        String id,
        String rawId,
        String type,
        String clientDataJSON,
        String attestationObject,
        List<String> transports
) {}

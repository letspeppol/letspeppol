package org.letspeppol.kyc.dto;

import java.time.Instant;

public record PasskeyDto(Long id, String displayName, Instant createdOn, Instant lastUsedOn) {}

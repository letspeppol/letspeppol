package org.letspeppol.kyc.dto;

import java.util.List;

public record TotpEnableResponse(List<String> recoveryCodes) {}

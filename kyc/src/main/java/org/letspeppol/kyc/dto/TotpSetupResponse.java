package org.letspeppol.kyc.dto;

public record TotpSetupResponse(String secret, String qrCodeDataUri) {}

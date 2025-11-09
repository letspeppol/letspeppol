package io.tubs.kyc.service.jwt;

public record JwtInfo(String token, String peppolId, String companyNumber, String uid) {}
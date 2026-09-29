package org.letspeppol.kyc.dto;

public record RegistrationReviewDecisionResponse(
        RegistrationReviewDto review,
        RegistrationResponse registration
) {}

package org.letspeppol.kyc.dto;

public record InvitationInfo(
        String email,
        String name,
        String companyName,
        String peppolId,
        boolean passwordRequired
) {}

package org.letspeppol.kyc.mapper;

import org.letspeppol.kyc.dto.CompanyUserDto;
import org.letspeppol.kyc.dto.InvitationInfo;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.CompanyPermission;
import org.letspeppol.kyc.model.Ownership;
import org.letspeppol.kyc.model.OwnershipInvitation;
import org.letspeppol.kyc.model.OwnershipStatus;

public class CompanyUserMapper {

    public static CompanyUserDto toCompanyUserDto(Ownership ownership, OwnershipInvitation invitation) {
        boolean invited = ownership.getStatus() == OwnershipStatus.INVITED;
        return new CompanyUserDto(
                ownership.getId(),
                invited ? invitedName(ownership, invitation) : ownership.getAccount().getName(),
                ownership.getAccount().getEmail(),
                ownership.getType(),
                ownership.getStatus(),
                CompanyPermission.effectiveMask(ownership.getType(), ownership.getPermissionMask()),
                ownership.getCreatedOn(),
                ownership.getLastUsed(),
                invited && invitation != null ? invitation.getExpiresOn() : null
        );
    }

    private static String invitedName(Ownership ownership, OwnershipInvitation invitation) {
        return invitation == null ? ownership.getAccount().getEmail() : invitation.getInvitedName();
    }

    public static InvitationInfo toInvitationInfo(OwnershipInvitation invitation) {
        Account account = invitation.getOwnership().getAccount();
        return new InvitationInfo(
                account.getEmail(),
                account.getName(),
                invitation.getOwnership().getCompany().getName(),
                invitation.getOwnership().getCompany().getPeppolId(),
                !account.isVerified()
        );
    }
}

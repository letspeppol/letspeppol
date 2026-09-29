package org.letspeppol.kyc.mapper;

import org.letspeppol.kyc.dto.RegistrationReviewDto;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.DirectorIdentityVerification;
import org.letspeppol.kyc.model.kbo.Company;

public class RegistrationReviewMapper {

    public static RegistrationReviewDto toDto(DirectorIdentityVerification verification, boolean companyHasAdmin) {
        Account account = verification.getAccount();
        Company company = verification.getDirector().getCompany();
        Account reviewedBy = verification.getReviewedBy();
        return new RegistrationReviewDto(
                verification.getId(),
                verification.getCreatedOn(),
                account.getEmail(),
                account.isVerified(),
                company.getPeppolId(),
                company.getName(),
                company.isSuspended(),
                companyHasAdmin,
                verification.getDirectorNameSnapshot(),
                verification.getCertificateSubject(),
                verification.getRequestedType(),
                verification.getReviewStatus(),
                reviewedBy == null ? null : reviewedBy.getEmail(),
                verification.getReviewedOn()
        );
    }
}

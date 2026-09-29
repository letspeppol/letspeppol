package org.letspeppol.kyc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.dto.RegistrationReviewDecisionResponse;
import org.letspeppol.kyc.dto.RegistrationReviewDto;
import org.letspeppol.kyc.dto.RegistrationResponse;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.exception.NotFoundException;
import org.letspeppol.kyc.mapper.RegistrationReviewMapper;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.DirectorIdentityVerification;
import org.letspeppol.kyc.model.Ownership;
import org.letspeppol.kyc.model.ReviewStatus;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.model.kbo.Director;
import org.letspeppol.kyc.repository.AccountIdentityVerificationRepository;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.letspeppol.kyc.repository.DirectorRepository;
import org.letspeppol.kyc.repository.OwnershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class RegistrationReviewService {

    private static final Set<ReviewStatus> APPROVABLE = EnumSet.of(ReviewStatus.PENDING, ReviewStatus.REJECTED);
    private static final Set<ReviewStatus> REJECTABLE = EnumSet.of(ReviewStatus.PENDING);

    private final AccountIdentityVerificationRepository verificationRepository;
    private final CompanyRepository companyRepository;
    private final DirectorRepository directorRepository;
    private final OwnershipRepository ownershipRepository;
    private final OwnershipService ownershipService;
    private final DirectorActivationService directorActivationService;

    public List<RegistrationReviewDto> list(ReviewStatus status) {
        List<DirectorIdentityVerification> reviews = switch (status) {
            case PENDING -> verificationRepository.findTop100ByReviewStatusOrderByCreatedOnAsc(status);
            case APPROVED, REJECTED -> verificationRepository.findTop100ByReviewStatusOrderByReviewedOnDesc(status);
            case NOT_REQUIRED -> throw new KycException(KycErrorCodes.INVALID_REVIEW_STATUS);
        };
        if (reviews.isEmpty()) {
            return List.of();
        }
        Set<Long> companyIds = reviews.stream().map(review -> companyOf(review).getId()).collect(Collectors.toSet());
        List<Ownership> admins = ownershipRepository.findByTypeAndCompanyIdIn(AccountType.ADMIN, companyIds);
        return reviews.stream()
                .map(review -> RegistrationReviewMapper.toDto(review, admins.stream().anyMatch(admin -> isOtherAdmin(admin, review))))
                .toList();
    }

    public DirectorIdentityVerification get(Long id) {
        return verificationRepository.findWithDetailsById(id)
                .orElseThrow(() -> new NotFoundException(KycErrorCodes.REVIEW_NOT_FOUND));
    }

    public RegistrationReviewDecisionResponse approve(Long id, Account reviewer) {
        DirectorIdentityVerification verification = lockForDecision(id, reviewer, APPROVABLE);
        if (companyHasOtherAdmin(verification)) {
            throw new KycException(KycErrorCodes.REVIEW_COMPANY_HAS_ADMIN);
        }
        Account account = verification.getAccount();
        Director director = verification.getDirector();
        Company company = director.getCompany();

        decide(verification, APPROVABLE, ReviewStatus.APPROVED, reviewer);
        director.setRegistered(true);
        directorRepository.save(director);

        RegistrationResponse registrationResponse = directorActivationService.activate(account, company, verification.getRequestedType());
        if (verification.getRequestedType() == AccountType.AFFILIATE) {
            ownershipService.ensureOwnership(account, AccountType.AFFILIATE, company);
        }
        log.info("Registration review {} approved by {} for account {} and company {}", id, reviewer.getEmail(), account.getEmail(), company.getPeppolId());
        return new RegistrationReviewDecisionResponse(RegistrationReviewMapper.toDto(verification, false), registrationResponse);
    }

    public RegistrationReviewDecisionResponse reject(Long id, Account reviewer) {
        DirectorIdentityVerification verification = lockForDecision(id, reviewer, REJECTABLE);
        decide(verification, REJECTABLE, ReviewStatus.REJECTED, reviewer);
        log.info("Registration review {} rejected by {} for account {} and company {}", id, reviewer.getEmail(),
                verification.getAccount().getEmail(), companyOf(verification).getPeppolId());
        return new RegistrationReviewDecisionResponse(RegistrationReviewMapper.toDto(verification, companyHasOtherAdmin(verification)), null);
    }

    private DirectorIdentityVerification lockForDecision(Long id, Account reviewer, Set<ReviewStatus> decidableStatuses) {
        Long companyId = verificationRepository.findCompanyIdById(id)
                .orElseThrow(() -> new NotFoundException(KycErrorCodes.REVIEW_NOT_FOUND));
        companyRepository.findForUpdateById(companyId);
        DirectorIdentityVerification verification = get(id);
        if (verification.getAccount().getId().equals(reviewer.getId())) {
            throw new KycException(KycErrorCodes.REVIEW_OWN_REGISTRATION);
        }
        if (!decidableStatuses.contains(verification.getReviewStatus())) {
            throw new KycException(KycErrorCodes.REVIEW_ALREADY_DECIDED);
        }
        return verification;
    }

    private void decide(DirectorIdentityVerification verification, Set<ReviewStatus> decidableStatuses, ReviewStatus decision, Account reviewer) {
        Instant now = Instant.now();
        List<DirectorIdentityVerification> undecided = verificationRepository.findByAccountIdAndDirectorCompanyIdAndReviewStatusIn(
                verification.getAccount().getId(), companyOf(verification).getId(), decidableStatuses);
        for (DirectorIdentityVerification review : undecided) {
            review.setReviewStatus(decision);
            review.setReviewedBy(reviewer);
            review.setReviewedOn(now);
        }
        verificationRepository.saveAll(undecided);
    }

    private boolean companyHasOtherAdmin(DirectorIdentityVerification verification) {
        return ownershipRepository.existsByCompanyIdAndTypeAndAccountIdNot(
                companyOf(verification).getId(), AccountType.ADMIN, verification.getAccount().getId());
    }

    private static boolean isOtherAdmin(Ownership admin, DirectorIdentityVerification review) {
        return admin.getCompany().getId().equals(companyOf(review).getId())
                && !admin.getAccount().getId().equals(review.getAccount().getId());
    }

    private static Company companyOf(DirectorIdentityVerification verification) {
        return verification.getDirector().getCompany();
    }
}

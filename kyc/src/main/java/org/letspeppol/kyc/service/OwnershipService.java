package org.letspeppol.kyc.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.dto.AuthRequest;
import org.letspeppol.kyc.dto.ServiceRequest;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.Ownership;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.repository.OwnershipRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class OwnershipService {

    private final OwnershipRepository ownershipRepository;
    private final AccountService accountService;
    private final CompanyService companyService;
    private final JwtService jwtService;
    private final ProxyService proxyService;

    public Ownership getByAccountExternalIdPeppolIdAndType(UUID uid, String peppolId, AccountType type) {
        return ownershipRepository.findFirstByAccountExternalIdAndCompanyPeppolIdAndTypeOrderByLastUsedDesc(uid, peppolId, type)
                .orElseThrow(() -> new KycException(KycErrorCodes.NO_OWNERSHIP));
    }

    public String generateAuthToken(Account account, AuthRequest request) {
        Ownership ownership = (request == null)
                ? ownershipRepository.findFirstByAccountIdOrderByLastUsedDesc(account.getId())
                .orElseThrow(() -> new KycException(KycErrorCodes.NO_OWNERSHIP))
                : ownershipRepository.findFirstByAccountIdAndCompanyPeppolIdAndTypeOrderByLastUsedDesc(account.getId(), request.peppolId(), request.type())
                .orElseThrow(() -> new KycException(KycErrorCodes.NO_OWNERSHIP));
        updateLastUsed(ownership);
        return jwtService.generateToken(
                ownership.getType(),
                ownership.getCompany().getPeppolId(),
                ownership.getCompany().isPeppolActive(),
                account.getExternalId()
        );
    }

    public String generateSwapToken(UUID uid, AuthRequest request) {
        Ownership ownership = getByAccountExternalIdPeppolIdAndType(uid, request.peppolId(), request.type());
        updateLastUsed(ownership);
        return jwtService.generateToken(
                ownership.getType(),
                ownership.getCompany().getPeppolId(),
                ownership.getCompany().isPeppolActive(),
                uid
        );
    }

    public Ownership getByPeppolIdAndType(String peppolId, AccountType type) {
        return ownershipRepository.findFirstByCompanyPeppolIdAndTypeOrderByLastUsedDesc(peppolId, type)
                .orElseThrow(() -> new KycException(KycErrorCodes.NO_OWNERSHIP));
    }

    public Ownership getByAccountEmailAndPeppolIdAndType(String email, String peppolId, AccountType type) {
        return ownershipRepository.findFirstByAccountEmailAndCompanyPeppolIdAndTypeOrderByCreatedOnDesc(email.toLowerCase(), peppolId, type)
                .orElseThrow(() -> new KycException(KycErrorCodes.NO_OWNERSHIP));
    }

    public List<Ownership> getByPeppolId(String peppolId) {
        return ownershipRepository.findByCompanyPeppolIdOrderByCreatedOnAsc(peppolId);
    }

    public void updateLastUsed(Ownership ownership) {
        ownership.setLastUsed(Instant.now());
        ownershipRepository.save(ownership);
    }

    public void verifyPeppolIdNotRegistered(String peppolId) {
        if (ownershipRepository.existsByTypeAndCompanyPeppolId(AccountType.ADMIN, peppolId)) { //TODO : maybe accounts of suspended companies can request again ? Or not make them ADMIN yet ?
            log.warn("User tried to register for company {} but was already registered", peppolId);
            throw new KycException(KycErrorCodes.COMPANY_ALREADY_REGISTERED);
        }
    }

    public Ownership link(Account account, AccountType type, Company company) {
        Ownership ownership = new Ownership(account, type, company);
        return ownershipRepository.save(ownership);
    }

    public Ownership ensureOwnership(Account account, AccountType type, Company company) {
        return ownershipRepository.findFirstByAccountIdAndCompanyIdAndType(account.getId(), company.getId(), type).orElseGet(() -> link(account, type, company));
    }

    public Ownership ensureAdminOwnership(Account account, Company company) {
        return ensureOwnership(account, AccountType.ADMIN, company);
    }

    public void unlink(UUID uid, AccountType type, String peppolId) {
        Ownership ownership = getByAccountExternalIdPeppolIdAndType(uid, peppolId, type);
        ownershipRepository.delete(ownership);
    }

    public Ownership linkServiceToAccount(String peppolId, UUID uid, ServiceRequest request) {
        Company company = companyService.getByPeppolId(peppolId);
        Account service = accountService.getByExternalId(request.uid());
        Ownership ownership = link(service, AccountType.APP, company);
        String token = jwtService.generateInternalToken(company.getPeppolId(), company.isPeppolActive(), uid);
        proxyService.allowService(token, request);
        return ownership;
    }

    public void unlinkServiceFromAccount(String peppolId, UUID uid, ServiceRequest request) {
        Company company = companyService.getByPeppolId(peppolId);
        unlink(request.uid(), AccountType.APP, peppolId);
        String token = jwtService.generateInternalToken(company.getPeppolId(), company.isPeppolActive(), uid);
        proxyService.rejectService(token, request);
    }
}

package org.letspeppol.kyc.service;

import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.RegistrationResponse;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.kbo.Company;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class DirectorActivationService {

    private final OwnershipService ownershipService;
    private final CompanyService companyService;
    private final Counter companyRegistrationCounterSuccess;
    private final Counter companyRegistrationCounterFailure;

    public RegistrationResponse activate(Account account, Company company, AccountType requestedType) {
        ownershipService.ensureAdminOwnership(account, company);
        if (requestedType != AccountType.ADMIN || company.isSuspended()) {
            return null;
        }
        RegistrationResponse registrationResponse = companyService.registerCompany(company);
        if (registrationResponse.peppolActive() && registrationResponse.errorCode() == null) {
            companyRegistrationCounterSuccess.increment();
        } else if (!registrationResponse.peppolActive()) {
            companyRegistrationCounterFailure.increment();
        }
        return registrationResponse;
    }
}

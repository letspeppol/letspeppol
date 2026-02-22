package org.letspeppol.kyc.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.model.Ownership;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.model.kbo.Director;
import org.letspeppol.kyc.repository.AccountRepository;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.letspeppol.kyc.repository.DirectorRepository;
import org.letspeppol.kyc.repository.OwnershipRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
@Profile("dev")
public class DataInitializer implements CommandLineRunner {

    private final AccountRepository accountRepository;
    private final OwnershipRepository ownershipRepository;
    private final CompanyRepository companyRepository;
    private final DirectorRepository directorRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        String companyNumber = "1023290711";
        if (companyRepository.findByPeppolId("0208:"+companyNumber).isEmpty()) {
            Company company = new Company("0208:"+companyNumber, "BE"+companyNumber, "SoftwareOplossing.be");
            company.setAddress("Bruxelles", "1000", "Rue Example 1");
            companyRepository.save(company);
            directorRepository.save(new Director("Bart In Stukken", company));
            directorRepository.save(new Director("Wout Schattebout", company));
            Account account = Account.builder()
                    .name("Bart In Stukken")
                    .email("test@softwareoplossing.be")
                    .passwordHash(passwordEncoder.encode("test"))
                    .externalId(UUID.randomUUID())
                    .build();
            accountRepository.save(account);
            Ownership ownership = new Ownership(account, AccountType.ADMIN, company);
            ownershipRepository.save(ownership);
            log.info("Seeded sample company {}", companyNumber);
        }
        companyNumber = "0705969661";
        if (companyRepository.findByPeppolId("0208:"+companyNumber).isEmpty()) {
            Company company = new Company("0208:"+companyNumber, "BE"+companyNumber, "Digita bv.");
            company.setAddress("Hasselt", "3500", "Demerstraat 2");
            companyRepository.save(company);
            directorRepository.save(new Director("Michiel Wouters", company));
            directorRepository.save(new Director("Saskia Verellen", company));
            Account account = Account.builder()
                    .name("Michiel Wouters")
                    .email("letspeppol@itaa.be")
                    .passwordHash(passwordEncoder.encode("letspeppol"))
                    .externalId(UUID.randomUUID())
                    .build();
            accountRepository.save(account);
            Ownership ownership = new Ownership(account, AccountType.ADMIN, company);
            ownershipRepository.save(ownership);
            log.info("Seeded sample company {}", companyNumber);
        }
        UUID appUUID = UUID.fromString("b095630d-1bf3-4250-bf9e-2d49e6ce505b");
        if (accountRepository.findByExternalId(appUUID).isEmpty()) {
            Company company = companyRepository.search("BE1029545627", null, null, null).stream()
                    .findFirst()
                    .orElseGet(() -> {
                        Company newCompany = new Company("0208:1029545627", "BE1029545627", "BARGE vzw");
                        companyRepository.save(newCompany);
                        directorRepository.save(new Director("Barst Brokken", newCompany));
                        return newCompany;
                    });
            Account account = Account.builder()
                    .name("Let’s Peppol")
                    .email("account@letspeppol.org")
                    .passwordHash(passwordEncoder.encode("letspeppol"))
                    .externalId(UUID.randomUUID())
                    .build();
            accountRepository.save(account);
            Ownership ownership = new Ownership(account, AccountType.ADMIN, company);
            ownershipRepository.save(ownership);
            Account appAccount = Account.builder()
                    .name("Let’s Peppol Email Notification App")
                    .email("support@letspeppol.org")
                    .passwordHash(passwordEncoder.encode("letspeppol"))
                    .externalId(appUUID)
                    .build();
            accountRepository.save(appAccount);
//            Ownership ownership = new Ownership(appAccount, AccountType.APP, company);
//            ownershipRepository.save(ownership);
            log.info("Seeded App account");
        }
        companyNumber = "0746936523";
        if (companyRepository.findByPeppolId("0208:"+companyNumber).isEmpty()) {
            Company company = new Company("0208:"+companyNumber, "BE"+companyNumber, "DIGITAL ACCOUNTANT");
            company.setAddress("Heusden-Zolder", "3550", "Belikstraat 109");
            companyRepository.save(company);
            directorRepository.save(new Director("Jurgen Wilmans", company));
            directorRepository.save(new Director("Alice Wonder", company));
            Account account = Account.builder()
                    .name("Alice")
                    .email("alice@letspeppol.org")
                    .passwordHash(passwordEncoder.encode("alice"))
                    .externalId(UUID.randomUUID())
                    .build();
            accountRepository.save(account);
            Ownership ownership = new Ownership(account, AccountType.ADMIN, company);
            ownershipRepository.save(ownership);
            Ownership accountantOwnership = new Ownership(account, AccountType.ACCOUNTANT, company);
            ownershipRepository.save(accountantOwnership);
            Account otherAccount = Account.builder()
                    .name("Bob")
                    .email("bob@letspeppol.org")
                    .passwordHash(passwordEncoder.encode("bob"))
                    .externalId(UUID.randomUUID())
                    .build();
            accountRepository.save(otherAccount);
            Ownership otherAccountantOwnership = new Ownership(otherAccount, AccountType.ACCOUNTANT, company);
            ownershipRepository.save(otherAccountantOwnership);
            log.info("Seeded Accountant account");
        }
    }
}

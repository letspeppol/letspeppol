package org.letspeppol.kyc.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.model.kbo.Director;
import org.letspeppol.kyc.repository.AccountRepository;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.letspeppol.kyc.repository.DirectorRepository;
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
    private final CompanyRepository companyRepository;
    private final DirectorRepository directorRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        String companyNumber = "1023290711";
        if (companyRepository.findByPeppolId("0208:"+companyNumber).isEmpty()) {
            Company c = new Company("0208:"+companyNumber, "BE"+companyNumber, "SoftwareOplossing.be", "Bruxelles", "1000", "Rue Example 1");
            companyRepository.save(c);
            directorRepository.save(new Director("Bart In Stukken", c));
            directorRepository.save(new Director("Wout Schattebout", c));
            Account account = Account.builder()
                    .company(c)
                    .name("Bart In Stukken")
                    .email("test@softwareoplossing.be")
                    .passwordHash(passwordEncoder.encode("test"))
                    .externalId(UUID.randomUUID())
                    .build();
            accountRepository.save(account);
            log.info("Seeded sample company {}", companyNumber);
        }
        companyNumber = "0705969661";
        if (companyRepository.findByPeppolId("0208:"+companyNumber).isEmpty()) {
            Company c = new Company("0208:"+companyNumber, "BE"+companyNumber, "Digita bv.", "Hasselt", "3500", "Demerstraat 2");
            companyRepository.save(c);
            directorRepository.save(new Director("Michiel Wouters", c));
            directorRepository.save(new Director("Saskia Verellen", c));
            Account account = Account.builder()
                    .company(c)
                    .name("Michiel Wouters")
                    .email("letspeppol@itaa.be")
                    .passwordHash(passwordEncoder.encode("letspeppol"))
                    .externalId(UUID.randomUUID())
                    .build();
            accountRepository.save(account);
            log.info("Seeded sample company {}", companyNumber);
        }
    }
}

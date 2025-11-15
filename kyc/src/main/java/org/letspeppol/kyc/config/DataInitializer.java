package org.letspeppol.kyc.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.model.User;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.model.kbo.Director;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.letspeppol.kyc.repository.DirectorRepository;
import org.letspeppol.kyc.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final DirectorRepository directorRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        String companyNumber = "1023290711";
        if (companyRepository.findByCompanyNumber(companyNumber).isEmpty()) {
            Company c = new Company(companyNumber, "SoftwareOplossing.be", "Bruxelles", "1000", "Rue Example", "1");
            companyRepository.save(c);
            directorRepository.save(new Director("Bart In Stukken", c));
            directorRepository.save(new Director("Wout Schattebout", c));
            User user = User.builder()
                    .company(c)
                    .email("test@softwareoplossing.be")
                    .passwordHash(passwordEncoder.encode("test"))
                    .externalId(UUID.randomUUID())
                    .build();
            userRepository.save(user);
            log.info("Seeded sample company {}", companyNumber);
        }
        companyNumber = "0705969661";
        if (companyRepository.findByCompanyNumber(companyNumber).isEmpty()) {
            Company c = new Company(companyNumber, "Digita bv.", "Hasselt", "3500", "Demerstraat", "2");
            companyRepository.save(c);
            directorRepository.save(new Director("Michiel Wouters", c));
            directorRepository.save(new Director("Saskia Verellen", c));
            User user = User.builder()
                    .company(c)
                    .email("letspeppol@itaa.be")
                    .passwordHash(passwordEncoder.encode("letspeppol"))
                    .externalId(UUID.randomUUID())
                    .build();
            userRepository.save(user);
            log.info("Seeded sample company {}", companyNumber);
        }
    }
}

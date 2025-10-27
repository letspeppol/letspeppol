package io.tubs.app.config;

import io.tubs.app.CompanyRepository;
import io.tubs.app.dto.AppRegistrationRequest;
import io.tubs.app.service.CompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Component
public class DataInitializer implements CommandLineRunner {

    private final CompanyRepository companyRepository;
    private final CompanyService companyService;

    @Override
    @Transactional
    public void run(String... args) {
        String companyNumber = "0705969661";
        if (companyRepository.findByCompanyNumber(companyNumber).isEmpty()) {
            AppRegistrationRequest registrationRequest = new AppRegistrationRequest(
                    companyNumber,
                    "Digita bv.",
                    "Demerstraat",
                    "2",
                    "Hasselt",
                    "3500",
                    "Michiel Wouters",
                    "michiel@digita.be"
            );
            companyService.register(registrationRequest);
        }
        companyNumber = "1023290711";
        if (companyRepository.findByCompanyNumber(companyNumber).isEmpty()) {
            AppRegistrationRequest registrationRequest = new AppRegistrationRequest(
                    companyNumber,
                    "SoftwareOplossing bv.",
                    "Demerstraat",
                    "2",
                    "Hasselt",
                    "3500",
                    "Bart In stukken",
                    "bart@softwareoplossing.be"
            );
            companyService.register(registrationRequest);
        }
    }
}

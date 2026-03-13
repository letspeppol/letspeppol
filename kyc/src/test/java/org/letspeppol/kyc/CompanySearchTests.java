package org.letspeppol.kyc;

import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.dto.CompanySearchResponse;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.letspeppol.kyc.service.CompanyService;
import org.letspeppol.kyc.service.kbo.KboXmlSyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class CompanySearchTests {

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private CompanyService companyService;

    @MockitoBean
    private JavaMailSender javaMailSender;

    @MockitoBean
    private KboXmlSyncService kboXmlSyncService;

    @Test
    void searchCompanies() {
        Company c1 = new Company("0208:1234567890", "BE1234567890", "Acme Corp");
        Company c2 = new Company("0208:0987654321", "BE0987654321", "Beta Inc");
        Company c3 = new Company("0208:1122334455", "BE1122334455", "Acme Limited");

        companyRepository.save(c1);
        companyRepository.save(c2);
        companyRepository.save(c3);

        // Search by name
        List<CompanySearchResponse> results = companyService.search(null, null, "Acme");
        assertThat(results).hasSize(2);
        assertThat(results).extracting(CompanySearchResponse::name).contains("Acme Corp", "Acme Limited");

        // Search by VAT
        results = companyService.search("BE0987654321", null, null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("Beta Inc");

        // Search by Peppol ID
        results = companyService.search(null, "0208:1122334455", null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("Acme Limited");

        // Search case insensitive
        results = companyService.search(null, null, "acme");
        assertThat(results).hasSize(2);
    }
}

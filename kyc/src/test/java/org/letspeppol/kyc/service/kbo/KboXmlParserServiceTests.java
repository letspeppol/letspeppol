package org.letspeppol.kyc.service.kbo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.model.kbo.Director;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KboXmlParserServiceTests {

    @Mock
    CompanyRepository companyRepository;

    @InjectMocks
    KboXmlParserService kboXmlParserService;

    @Test
    @DisplayName("importEnterprises should create companies and directors for enterprises with address and active functions held by a person")
    void importEnterprisesCreatesCompaniesAndDirectors() throws Exception {
        when(companyRepository.findByPeppolId(anyString())).thenReturn(Optional.empty());

        InputStream is = getClass().getResourceAsStream("/D20251101.xml");
        assertNotNull(is, "Test XML resource D20251101.xml should be on classpath");

        // Capture what is persisted
        ArgumentCaptor<List<Company>> captor = ArgumentCaptor.forClass(List.class);
        when(companyRepository.saveAll(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Company> result = kboXmlParserService.importEnterprises(is);

        assertNotNull(result);
        // Two enterprises (200762878 and 200881951) match the criteria
        assertEquals(2, result.size());

        Company company1 = result.stream().filter(c -> "200762878".equals(c.getVatNumber())).findFirst().orElseThrow();
        Company company2 = result.stream().filter(c -> "200881951".equals(c.getVatNumber())).findFirst().orElseThrow();

        // Check company 1
        assertEquals("VLOTTER", company1.getName());
        assertEquals("Boom", company1.getCity());
        assertEquals("2850", company1.getPostalCode());
        assertEquals("Colonel Silvertopstraat", company1.getStreet());
        assertEquals("15", company1.getHouseNumber());

        List<Director> directors1 = company1.getDirectors();
        assertEquals(2, directors1.size());
        List<String> directorNames1 = directors1.stream().map(Director::getName).toList();
        assertTrue(directorNames1.contains("Go Van Dy"));
        assertTrue(directorNames1.contains("Bary De Smet"));

        // Check company 2
        assertEquals("Intercommunale Maatschappij voor de Ruimtelijke Ordening en de Economisch- Sociale Expansie van het Arrondissement Halle-Vilvoorde", company2.getName());
        assertEquals("Asse", company2.getCity());
        assertEquals("1731", company2.getPostalCode());
        assertEquals("Brusselsesteenweg", company2.getStreet());
        assertEquals("617", company2.getHouseNumber());

        List<Director> directors2 = company2.getDirectors();
        assertEquals(2, directors2.size());
        List<String> directorNames2 = directors2.stream().map(Director::getName).toList();
        assertTrue(directorNames2.contains("Liev Imbrec"));
        assertTrue(directorNames2.contains("Diet Phili"));

        // Enterprise 200450005 has no Functions, so must not be persisted
        assertTrue(result.stream().noneMatch(c -> "200450005".equals(c.getVatNumber())));
    }

    @Test
    @DisplayName("importEnterprises should update existing company instead of creating new one")
    void importEnterprisesUpdatesExistingCompany() throws Exception {
        InputStream is = getClass().getResourceAsStream("/D20251101.xml");
        assertNotNull(is);

        Company existing = new Company("BE200762878", "200762878", "Old name", "OldCity", "0000", "OldStreet", "1");
        existing.setDirectors(new ArrayList<>());

        when(companyRepository.findByPeppolId("BE200762878")).thenReturn(Optional.of(existing));
        when(companyRepository.findByPeppolId("BE200881951")).thenReturn(Optional.empty());
        when(companyRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Company> result = kboXmlParserService.importEnterprises(is);

        Company updated = result.stream().filter(c -> "200762878".equals(c.getVatNumber())).findFirst().orElseThrow();
        assertSame(existing, updated);
        assertEquals("VLOTTER", updated.getName());
        assertEquals("Boom", updated.getCity());
        assertEquals("2850", updated.getPostalCode());
        assertEquals("Colonel Silvertopstraat", updated.getStreet());
        assertEquals("15", updated.getHouseNumber());
        assertEquals(2, updated.getDirectors().size());
    }

    @Test
    @DisplayName("importEnterprises should keep registered directors even if not present in KBO XML")
    void importEnterprisesKeepsRegisteredDirectors() throws Exception {
        InputStream is = getClass().getResourceAsStream("/D20251101.xml");
        assertNotNull(is);

        // Existing company with a registered director that does not appear in the XML
        Company existing = new Company("BE200762878", "200762878", "Old name", "OldCity", "0000", "OldStreet", "1");
        Director registeredDirector = new Director("Legacy Director", existing);
        registeredDirector.setRegistered(true);
        existing.getDirectors().add(registeredDirector);

        when(companyRepository.findByPeppolId("BE200762878")).thenReturn(Optional.of(existing));
        when(companyRepository.findByPeppolId("BE200881951")).thenReturn(Optional.empty());
        when(companyRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<Company> result = kboXmlParserService.importEnterprises(is);

        Company updated = result.stream().filter(c -> "200762878".equals(c.getVatNumber())).findFirst().orElseThrow();

        // The registered director should still be present after import
        List<String> directorNames = updated.getDirectors().stream().map(Director::getName).toList();
        assertTrue(directorNames.contains("Legacy Director"));

        // And the KBO directors should also be there
        assertTrue(directorNames.contains("Go Van Dy"));
        assertTrue(directorNames.contains("Bary De Smet"));
    }
}

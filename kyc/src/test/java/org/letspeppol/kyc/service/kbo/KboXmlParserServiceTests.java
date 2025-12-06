package org.letspeppol.kyc.service.kbo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.model.kbo.Director;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KboXmlParserServiceTests {

    @Mock
    CompanyRepository companyRepository;

    @Mock
    KboBatchPersistenceService kboBatchPersistenceService;

    private KboXmlParserService kboXmlParserService;

    @BeforeEach
    void setUp() {
        kboXmlParserService = new KboXmlParserService(companyRepository, kboBatchPersistenceService);
        kboXmlParserService.setDefaultBatchSize(1);
    }

    @Test
    @DisplayName("importEnterprises should create companies and directors for enterprises with address and active functions held by a person")
    void importEnterprisesCreatesCompaniesAndDirectors() throws Exception {
        when(companyRepository.findWithDirectorsByPeppolId(anyString())).thenReturn(Optional.empty());

        InputStream is = getClass().getResourceAsStream("/D20251101.xml");
        assertNotNull(is, "Test XML resource D20251101.xml should be on classpath");

        List<Company> persistedCompanies = new ArrayList<>();
        when(kboBatchPersistenceService.saveBatch(anyList())).thenAnswer(invocation -> {
            List<Company> batch = invocation.getArgument(0);
            persistedCompanies.addAll(batch);
            return batch;
        });

        kboXmlParserService.importEnterprises(is);

        assertFalse(persistedCompanies.isEmpty());
        // Two enterprises (200762878 and 200881951) match the criteria
        assertEquals(2, persistedCompanies.size());

        Company company1 = persistedCompanies.stream().filter(c -> "BE0200762878".equals(c.getVatNumber())).findFirst().orElseThrow();
        Company company2 = persistedCompanies.stream().filter(c -> "BE0200881951".equals(c.getVatNumber())).findFirst().orElseThrow();

        // Check company 1
        assertEquals("VLOTTER", company1.getName());
        assertEquals("Boom", company1.getCity());
        assertEquals("2850", company1.getPostalCode());
        assertEquals("Colonel Silvertopstraat 15", company1.getStreet());

        List<Director> directors1 = company1.getDirectors();
        assertEquals(2, directors1.size());
        List<String> directorNames1 = directors1.stream().map(Director::getName).toList();
        assertTrue(directorNames1.contains("Go Van Dy"));
        assertTrue(directorNames1.contains("Bary De Smet"));

        // Check company 2
        assertEquals("Intercommunale Maatschappij voor de Ruimtelijke Ordening en de Economisch- Sociale Expansie van het Arrondissement Halle-Vilvoorde", company2.getName());
        assertEquals("Asse", company2.getCity());
        assertEquals("1731", company2.getPostalCode());
        assertEquals("Brusselsesteenweg 617", company2.getStreet());

        List<Director> directors2 = company2.getDirectors();
        assertEquals(2, directors2.size());
        List<String> directorNames2 = directors2.stream().map(Director::getName).toList();
        assertTrue(directorNames2.contains("Liev Imbrec"));
        assertTrue(directorNames2.contains("Diet Phili"));

        // Enterprise 200450005 has no Functions, so must not be persisted
        assertTrue(persistedCompanies.stream().noneMatch(c -> "BE200450005".equals(c.getVatNumber())));
    }

    @Test
    @DisplayName("importEnterprises should update existing company instead of creating new one")
    void importEnterprisesUpdatesExistingCompany() throws Exception {
        InputStream is = getClass().getResourceAsStream("/D20251101.xml");
        assertNotNull(is);

        Company existing = new Company("0208:200762878", "BE0200762878", "Old name");
        existing.setAddress("OldCity", "0000", "OldStreet 1");
        existing.setDirectors(new ArrayList<>());

        when(companyRepository.findWithDirectorsByPeppolId("0208:0200762878")).thenReturn(Optional.of(existing));
        when(companyRepository.findWithDirectorsByPeppolId("0208:0200881951")).thenReturn(Optional.empty());

        List<Company> persistedCompanies = new ArrayList<>();
        when(kboBatchPersistenceService.saveBatch(anyList())).thenAnswer(invocation -> {
            List<Company> batch = invocation.getArgument(0);
            persistedCompanies.addAll(batch);
            return batch;
        });

        kboXmlParserService.importEnterprises(is);

        Company updated = persistedCompanies.stream().filter(c -> "BE0200762878".equals(c.getVatNumber())).findFirst().orElseThrow();
        assertSame(existing, updated);
        assertEquals("VLOTTER", updated.getName());
        assertEquals("Boom", updated.getCity());
        assertEquals("2850", updated.getPostalCode());
        assertEquals("Colonel Silvertopstraat 15", updated.getStreet());
        assertEquals(2, updated.getDirectors().size());
    }

    @Test
    @DisplayName("importEnterprises should keep registered directors even if not present in KBO XML")
    void importEnterprisesKeepsRegisteredDirectors() throws Exception {
        InputStream is = getClass().getResourceAsStream("/D20251101.xml");
        assertNotNull(is);

        // Existing company with a registered director that does not appear in the XML
        Company existing = new Company("0208:0200762878", "BE0200762878", "Old name");
        existing.setAddress("OldCity", "0000", "OldStreet 1");
        Director registeredDirector = new Director("Legacy Director", existing);
        registeredDirector.setRegistered(true);
        existing.getDirectors().add(registeredDirector);

        when(companyRepository.findWithDirectorsByPeppolId("0208:0200762878")).thenReturn(Optional.of(existing));
        when(companyRepository.findWithDirectorsByPeppolId("0208:0200881951")).thenReturn(Optional.empty());

        List<Company> persistedCompanies = new ArrayList<>();
        when(kboBatchPersistenceService.saveBatch(anyList())).thenAnswer(invocation -> {
            List<Company> batch = invocation.getArgument(0);
            persistedCompanies.addAll(batch);
            return batch;
        });

        kboXmlParserService.importEnterprises(is);

        Company updated = persistedCompanies.stream().filter(c -> "BE0200762878".equals(c.getVatNumber())).findFirst().orElseThrow();

        // The registered director should still be present after import
        List<String> directorNames = updated.getDirectors().stream().map(Director::getName).toList();
        assertTrue(directorNames.contains("Legacy Director"));

        // And the KBO directors should also be there
        assertTrue(directorNames.contains("Go Van Dy"));
        assertTrue(directorNames.contains("Bary De Smet"));
    }

    @Test
    @DisplayName("importEnterprises should skip persistence when company and directors are unchanged")
    void importEnterprisesSkipsWhenUnchanged() throws Exception {
        InputStream is = getClass().getResourceAsStream("/D20251101.xml");
        assertNotNull(is);

        Company existing1 = new Company("0208:0200762878", "BE0200762878", "VLOTTER");
        existing1.setAddress("Boom", "2850", "Colonel Silvertopstraat 15");
        existing1.setId(1L);
        existing1.setDirectors(new ArrayList<>(List.of(new Director("Go Van Dy", existing1), new Director("Bary De Smet", existing1))));
        Company existing2 = new Company("0208:0200881951", "BE0200881951", "Intercommunale Maatschappij voor de Ruimtelijke Ordening en de Economisch- Sociale Expansie van het Arrondissement Halle-Vilvoorde");
        existing2.setAddress("Asse", "1731", "Brusselsesteenweg 617");
        existing2.setId(2L);
        existing2.setDirectors(new ArrayList<>(List.of(new Director("Liev Imbrec", existing2), new Director("Diet Phili", existing2))));

        when(companyRepository.findWithDirectorsByPeppolId("0208:0200762878")).thenReturn(Optional.of(existing1));
        when(companyRepository.findWithDirectorsByPeppolId("0208:0200881951")).thenReturn(Optional.of(existing2));

        ArgumentCaptor<List<Company>> batchCaptor = ArgumentCaptor.forClass(List.class);
        when(kboBatchPersistenceService.saveBatch(batchCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        kboXmlParserService.importEnterprises(is);

        assertTrue(batchCaptor.getAllValues().isEmpty(), "No persistence should happen for unchanged enterprises");
    }

    @Test
    @DisplayName("importEnterprises should persist when directors differ from enterprise data")
    void importEnterprisesPersistsWhenDirectorsChange() throws Exception {
        InputStream is = getClass().getResourceAsStream("/D20251101.xml");
        assertNotNull(is);

        Company existing = new Company("0208:0200762878", "BE0200762878", "VLOTTER");
        existing.setAddress("Boom", "2850", "Colonel Silvertopstraat 15");
        existing.setId(3L);
        Director legacyDirector = new Director("Legacy Director", existing);
        legacyDirector.setRegistered(true);
        existing.setDirectors(new ArrayList<>(List.of(legacyDirector)));

        when(companyRepository.findWithDirectorsByPeppolId("0208:0200762878")).thenReturn(Optional.of(existing));
        when(companyRepository.findWithDirectorsByPeppolId("0208:0200881951")).thenReturn(Optional.empty());

        ArgumentCaptor<List<Company>> batchCaptor = ArgumentCaptor.forClass(List.class);
        when(kboBatchPersistenceService.saveBatch(batchCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        kboXmlParserService.importEnterprises(is);

        assertFalse(batchCaptor.getAllValues().isEmpty(), "Changes to directors should trigger persistence");
        List<String> directorNames = existing.getDirectors().stream().map(Director::getName).toList();
        assertEquals(3, directorNames.size());
        assertTrue(directorNames.contains("Go Van Dy"));
        assertTrue(directorNames.contains("Bary De Smet"));
        assertTrue(directorNames.contains("Legacy Director"));
    }

    @Test
    @DisplayName("importEnterprises should schedule deletion when enterprise validity has an end date")
    void importEnterprisesDeletesEndedEnterprise() throws Exception {
        // No registered directors for this Peppol ID
        when(companyRepository.existsRegisteredDirectorForPeppolId("0208:0404356574"))
                .thenReturn(false);

        InputStream is = getClass().getResourceAsStream("/D202511EndEnterprise.xml");
        assertNotNull(is, "Test XML resource D202511EndEnterprise.xml should be on classpath");

        kboXmlParserService.importEnterprises(is);

        // No upserts should be scheduled for this ended enterprise
        verify(kboBatchPersistenceService, never()).saveBatch(anyList());

        // Deletion should be scheduled with the proper Peppol ID
        ArgumentCaptor<List<String>> deleteCaptor = ArgumentCaptor.forClass(List.class);
        verify(kboBatchPersistenceService, times(1)).deleteByPeppolIds(deleteCaptor.capture());

        List<String> deletedIds = deleteCaptor.getValue();
        assertEquals(1, deletedIds.size());
        assertEquals("0208:0404356574", deletedIds.get(0));
    }

    @Test
    @DisplayName("importEnterprises should not delete company for ended enterprise when it has registered directors")
    void importEnterprisesDoesNotDeleteEndedEnterpriseWithRegisteredDirectors() throws Exception {
        // There is at least one registered director for this Peppol ID
        when(companyRepository.existsRegisteredDirectorForPeppolId("0208:0404356574"))
                .thenReturn(true);

        InputStream is = getClass().getResourceAsStream("/D202511EndEnterprise.xml");
        assertNotNull(is, "Test XML resource D202511EndEnterprise.xml should be on classpath");

        kboXmlParserService.importEnterprises(is);

        // No upserts should be scheduled for this ended enterprise
        verify(kboBatchPersistenceService, never()).saveBatch(anyList());

        // And deletion should NOT be scheduled when there is a registered director
        verify(kboBatchPersistenceService, never()).deleteByPeppolIds(anyList());
    }

    @Test
    @DisplayName("importEnterprises should enrich company address from business units when enterprise has no address")
    void importEnterprisesEnrichesAddressFromBusinessUnit() throws Exception {
        // Enterprise number from D20251101_BusinessUnit.xml without address but with directors
        String enterpriseNbr = "632789963";
        String peppolId = "0208:0" + enterpriseNbr;
        String vatNumber = "BE0" + enterpriseNbr;

        // Existing company without KBO address, linked to the business unit number in the XML
        Company existing = new Company(peppolId, vatNumber, "BITS");
        existing.setBusinessUnit("2292261537");
        existing.setHasKboAddress(false);
        existing.setDirectors(new ArrayList<>());

        when(companyRepository.findWithDirectorsByPeppolId(peppolId)).thenReturn(Optional.of(existing));
        when(companyRepository.findByBusinessUnitAndHasKboAddressFalse("2292261537")).thenReturn(Optional.of(existing));

        // Capture save calls from business unit processing
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // We don't care about batch upserts here
        when(kboBatchPersistenceService.saveBatch(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        InputStream is = getClass().getResourceAsStream("/D20251101_BusinessUnit.xml");
        assertNotNull(is, "Test XML resource D20251101_BusinessUnit.xml should be on classpath");

        kboXmlParserService.importEnterprises(is);

        // After processing, the existing company should have its address set from the BusinessUnit
        assertEquals("Hasselt", existing.getCity());
        assertEquals("3500", existing.getPostalCode());
        assertEquals("Demerstraat 64", existing.getStreet());
        assertTrue(existing.isHasKboAddress());

        // Verify that the repository save was invoked to persist the updated address
        verify(companyRepository, atLeastOnce()).save(existing);
    }
}

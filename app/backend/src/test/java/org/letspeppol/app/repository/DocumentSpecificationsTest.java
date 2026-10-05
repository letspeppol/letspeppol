package org.letspeppol.app.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letspeppol.app.PostgresIntegrationTest;
import org.letspeppol.app.dto.DocumentFilter;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.Document;
import org.letspeppol.app.model.DocumentDirection;
import org.letspeppol.app.model.DocumentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class DocumentSpecificationsTest extends PostgresIntegrationTest {

    private static final String OWNER = "0208:0000000001";
    private static final String OTHER_OWNER = "0208:0000000002";

    @Autowired private DocumentRepository documentRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EntityManager em;

    private Company ownerCompany;
    private Company otherCompany;

    @BeforeEach
    void setUpCompanies() {
        ownerCompany = saveCompany(OWNER);
        otherCompany = saveCompany(OTHER_OWNER);
    }

    private Company saveCompany(String peppolId) {
        return companyRepository.saveAndFlush(new Company(
                peppolId, "0123456789", "BE0123456789", "Name " + peppolId, "Sub", "sub@example.com",
                "Brussels", "1000", "Rue de Test 1", "BE"));
    }

    private void persist(String reference, Consumer<Document> configurer) {
        persist(ownerCompany, OWNER, reference, configurer);
    }

    private void persist(Company company, String ownerPeppolId, String reference, Consumer<Document> configurer) {
        Document d = new Document();
        d.setId(UUID.randomUUID());
        d.setDirection(DocumentDirection.OUTGOING);
        d.setType(DocumentType.INVOICE);
        d.setOwnerPeppolId(ownerPeppolId);
        d.setPartnerPeppolId("0208:9999999999");
        d.setPartnerName("Partner");
        d.setInvoiceReference(reference);
        d.setCurrency(Currency.getInstance("EUR"));
        d.setCompany(company);
        d.setAmountInclVat(new BigDecimal("100.00"));
        d.setIssueDate(daysAgo(60));
        d.setCreatedExternally(false);
        configurer.accept(d);
        em.persist(d);
    }

    private void persistStatusMix() {
        persist("OVERDUE", d -> d.setDueDate(daysAgo(1)));
        persist("NOT-YET-DUE", d -> d.setDueDate(daysAhead(1)));
        persist("NO-DUE-DATE", d -> d.setDueDate(null));
        persist("PAID-LATE", d -> {
            d.setDueDate(daysAgo(1));
            d.setPaidOn(Instant.now());
        });
        persist("DRAFT-PAST-DUE", d -> {
            d.setDueDate(daysAgo(1));
            d.setDraftedOn(Instant.now());
        });
        persist("ERRORED-PAST-DUE", d -> {
            d.setDueDate(daysAgo(1));
            d.setProcessedStatus("Receiver not found");
        });
        persist(otherCompany, OTHER_OWNER, "OTHER-OWNER-OVERDUE", d -> d.setDueDate(daysAgo(1)));
        em.flush();
    }

    private List<String> references(DocumentFilter filter) {
        filter.setOwnerPeppolId(OWNER);
        return documentRepository.findAll(DocumentSpecifications.build(filter)).stream()
                .map(Document::getInvoiceReference)
                .toList();
    }

    private static Instant daysAgo(long days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }

    private static Instant daysAhead(long days) {
        return Instant.now().plus(days, ChronoUnit.DAYS);
    }

    @Test
    void overdueMatchesUnpaidSentDocumentsPastTheirDueDate() {
        persistStatusMix();
        DocumentFilter filter = new DocumentFilter();
        filter.setOverdue(true);

        assertThat(references(filter)).containsExactly("OVERDUE");
    }

    @Test
    void notOverdueMatchesEveryOtherDocument() {
        persistStatusMix();
        DocumentFilter filter = new DocumentFilter();
        filter.setOverdue(false);

        assertThat(references(filter)).containsExactlyInAnyOrder(
                "NOT-YET-DUE", "NO-DUE-DATE", "PAID-LATE", "DRAFT-PAST-DUE", "ERRORED-PAST-DUE");
    }

    @Test
    void erroredMatchesDocumentsWithAProcessedStatus() {
        persistStatusMix();
        DocumentFilter filter = new DocumentFilter();
        filter.setErrored(true);

        assertThat(references(filter)).containsExactly("ERRORED-PAST-DUE");
    }

    @Test
    void notErroredMatchesDocumentsWithoutAProcessedStatus() {
        persistStatusMix();
        DocumentFilter filter = new DocumentFilter();
        filter.setErrored(false);

        assertThat(references(filter)).containsExactlyInAnyOrder(
                "OVERDUE", "NOT-YET-DUE", "NO-DUE-DATE", "PAID-LATE", "DRAFT-PAST-DUE");
    }
}

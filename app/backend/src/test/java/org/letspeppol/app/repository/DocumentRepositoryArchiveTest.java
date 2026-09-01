package org.letspeppol.app.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letspeppol.app.PostgresIntegrationTest;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.Document;
import org.letspeppol.app.model.DocumentDirection;
import org.letspeppol.app.model.DocumentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class DocumentRepositoryArchiveTest extends PostgresIntegrationTest {

    private static final String OWNER = "0208:1000000001";
    private static final String OTHER_OWNER = "0208:1000000002";

    @Autowired DocumentRepository documentRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired EntityManager entityManager;

    private Company owner;
    private Company otherOwner;

    @BeforeEach
    void setUp() {
        owner = saveCompany(OWNER);
        otherOwner = saveCompany(OTHER_OWNER);
    }

    @Test
    void selectsInclusiveFinalizedTenantDocumentsWithAvailableUbl() {
        UUID incomingInvoice = persist(owner, OWNER, DocumentDirection.INCOMING, DocumentType.INVOICE,
                "2026-06-01", null, "<Invoice/>");
        UUID outgoingInvoice = persist(owner, OWNER, DocumentDirection.OUTGOING, DocumentType.INVOICE,
                "2026-06-10", null, "<Invoice/>");
        UUID incomingCreditNote = persist(owner, OWNER, DocumentDirection.INCOMING, DocumentType.CREDIT_NOTE,
                "2026-06-20", null, "<CreditNote/>");
        UUID outgoingCreditNote = persist(owner, OWNER, DocumentDirection.OUTGOING, DocumentType.CREDIT_NOTE,
                "2026-06-30", null, "<CreditNote/>");

        persist(owner, OWNER, DocumentDirection.OUTGOING, DocumentType.INVOICE,
                "2026-06-15", Instant.now(), "<Draft/>");
        persist(owner, OWNER, DocumentDirection.INCOMING, DocumentType.INVOICE,
                "2026-06-15", null, null);
        persist(owner, OWNER, DocumentDirection.INCOMING, DocumentType.INVOICE,
                "2026-07-01", null, "<Outside/>");
        persist(otherOwner, OTHER_OWNER, DocumentDirection.INCOMING, DocumentType.INVOICE,
                "2026-06-15", null, "<OtherTenant/>");
        entityManager.flush();

        Instant start = LocalDate.parse("2026-06-01").atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endExclusive = LocalDate.parse("2026-07-01").atStartOfDay(ZoneOffset.UTC).toInstant();
        List<UUID> ids = documentRepository.findAllForArchive(OWNER, start, endExclusive).stream()
                .map(Document::getId)
                .toList();

        assertThat(ids).containsExactly(incomingInvoice, outgoingInvoice, incomingCreditNote, outgoingCreditNote);
        assertThat(documentRepository.existsForArchive(OWNER, start, endExclusive)).isTrue();
        assertThat(documentRepository.existsForArchive(OWNER,
                LocalDate.parse("2026-08-01").atStartOfDay(ZoneOffset.UTC).toInstant(),
                LocalDate.parse("2026-09-01").atStartOfDay(ZoneOffset.UTC).toInstant())).isFalse();
    }

    private Company saveCompany(String peppolId) {
        return companyRepository.saveAndFlush(new Company(
                peppolId, peppolId.substring(5), "BE0123456789", "Company " + peppolId,
                "Subscriber", "subscriber@example.com", "Brussels", "1000", "Test street 1", "BE"));
    }

    private UUID persist(Company company, String peppolId, DocumentDirection direction, DocumentType type,
                         String issueDate, Instant draftedOn, String ubl) {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setCompany(company);
        document.setOwnerPeppolId(peppolId);
        document.setPartnerPeppolId("0208:9999999999");
        document.setPartnerName("Partner");
        document.setInvoiceReference(type + "-" + UUID.randomUUID());
        document.setDirection(direction);
        document.setType(type);
        document.setCurrency(Currency.getInstance("EUR"));
        document.setAmountInclVat(BigDecimal.TEN);
        document.setAmountExclVat(BigDecimal.ONE);
        document.setIssueDate(LocalDate.parse(issueDate).atStartOfDay(ZoneOffset.UTC).toInstant());
        document.setDraftedOn(draftedOn);
        document.setUbl(ubl);
        entityManager.persist(document);
        return document.getId();
    }
}

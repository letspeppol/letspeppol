package org.letspeppol.app.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letspeppol.app.PostgresIntegrationTest;
import org.letspeppol.app.dto.TotalsDto;
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
import java.time.Month;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
class DocumentRepositoryTotalsTest extends PostgresIntegrationTest {

    private static final String OWNER = "0208:0000000001";
    private static final String OTHER_OWNER = "0208:0000000002";
    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency USD = Currency.getInstance("USD");

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
                peppolId, "BE0123456789", "Name " + peppolId, "Sub", "sub@example.com",
                "Brussels", "1000", "Rue de Test 1", "BE"));
    }

    private Document baseDocument(Company company, String ownerPeppolId) {
        Document d = new Document();
        d.setId(UUID.randomUUID());
        d.setDirection(DocumentDirection.INCOMING);
        d.setType(DocumentType.INVOICE);
        d.setOwnerPeppolId(ownerPeppolId);
        d.setPartnerPeppolId("0208:9999999999");
        d.setPartnerName("Partner");
        d.setInvoiceReference("REF-" + UUID.randomUUID());
        d.setCurrency(EUR);
        d.setCompany(company);
        d.setAmount(new BigDecimal("100.00"));
        d.setIssueDate(Instant.now());
        return d;
    }

    private void persist(Consumer<Document> configurer) {
        Document d = baseDocument(ownerCompany, OWNER);
        configurer.accept(d);
        em.persist(d);
    }

    private void persistOther(Consumer<Document> configurer) {
        Document d = baseDocument(otherCompany, OTHER_OWNER);
        configurer.accept(d);
        em.persist(d);
    }

    private void flush() {
        em.flush();
    }

    private static Instant daysAgo(long days) {
        return Instant.now().minus(days, ChronoUnit.DAYS);
    }

    private static Instant daysAhead(long days) {
        return Instant.now().plus(days, ChronoUnit.DAYS);
    }

    private static Instant lastYear() {
        int year = LocalDate.now().getYear() - 1;
        return LocalDate.of(year, Month.JUNE, 15).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    @Test
    void returnsZeroesWhenOwnerHasNoDocuments() {
        persistOther(d -> d.setAmount(new BigDecimal("500.00")));
        flush();

        TotalsDto totals = documentRepository.totalsByOwner(OWNER);

        assertThat(totals.totalPayableOpen()).isEqualByComparingTo("0");
        assertThat(totals.totalPayableOverdue()).isEqualByComparingTo("0");
        assertThat(totals.totalPayableThisYear()).isEqualByComparingTo("0");
        assertThat(totals.totalReceivableOpen()).isEqualByComparingTo("0");
        assertThat(totals.totalReceivableOverdue()).isEqualByComparingTo("0");
        assertThat(totals.totalReceivableThisYear()).isEqualByComparingTo("0");
    }

    @Test
    void incomingCreditNoteSubtractsFromPayableTotals() {
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.INVOICE);
            d.setAmount(new BigDecimal("100.00"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.INVOICE);
            d.setAmount(new BigDecimal("49.50"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmount(new BigDecimal("30.00"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmount(new BigDecimal("12.50"));
        });
        // Noise: drafted credit note must NOT subtract
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmount(new BigDecimal("777.00"));
            d.setDraftedOn(Instant.now());
        });
        // Noise: outgoing credit note must not touch payable totals
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmount(new BigDecimal("400.00"));
        });
        // Noise: other owner's credit note must not subtract
        persistOther(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmount(new BigDecimal("999.00"));
        });
        flush();

        TotalsDto totals = documentRepository.totalsByOwner(OWNER);

        assertThat(totals.totalPayableOpen()).isEqualByComparingTo("107.00");
        assertThat(totals.totalPayableThisYear()).isEqualByComparingTo("107.00");
    }

    @Test
    void outgoingCreditNoteSubtractsFromReceivableTotals() {
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.INVOICE);
            d.setAmount(new BigDecimal("250.00"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.INVOICE);
            d.setAmount(new BigDecimal("125.75"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmount(new BigDecimal("40.00"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmount(new BigDecimal("15.25"));
        });
        // Noise: incoming invoice/credit note must not shift receivables
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.INVOICE);
            d.setAmount(new BigDecimal("600.00"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmount(new BigDecimal("60.00"));
        });
        flush();

        TotalsDto totals = documentRepository.totalsByOwner(OWNER);

        assertThat(totals.totalReceivableOpen()).isEqualByComparingTo("320.50");
        assertThat(totals.totalReceivableThisYear()).isEqualByComparingTo("320.50");
    }

    @Test
    void draftsAreExcluded() {
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmount(new BigDecimal("100.00"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmount(new BigDecimal("999.00"));
            d.setDraftedOn(Instant.now());
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmount(new BigDecimal("888.00"));
            d.setDraftedOn(Instant.now().minus(1, ChronoUnit.DAYS));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmount(new BigDecimal("777.00"));
            d.setDraftedOn(Instant.now());
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmount(new BigDecimal("42.00"));
        });
        flush();

        TotalsDto totals = documentRepository.totalsByOwner(OWNER);

        assertThat(totals.totalPayableOpen()).isEqualByComparingTo("100.00");
        assertThat(totals.totalPayableThisYear()).isEqualByComparingTo("100.00");
        assertThat(totals.totalReceivableOpen()).isEqualByComparingTo("42.00");
        assertThat(totals.totalReceivableThisYear()).isEqualByComparingTo("42.00");
    }

    @Test
    void paidDocumentsAreExcludedFromOpenButCountInThisYear() {
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmount(new BigDecimal("200.00"));
            d.setPaidOn(Instant.now());
            d.setDueDate(daysAgo(30));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmount(new BigDecimal("50.00"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmount(new BigDecimal("20.00"));
            d.setPaidOn(Instant.now());
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmount(new BigDecimal("300.00"));
            d.setPaidOn(Instant.now());
            d.setDueDate(daysAgo(10));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmount(new BigDecimal("75.00"));
        });
        flush();

        TotalsDto totals = documentRepository.totalsByOwner(OWNER);

        assertThat(totals.totalReceivableOpen()).isEqualByComparingTo("50.00");
        assertThat(totals.totalReceivableOverdue()).isEqualByComparingTo("0");
        assertThat(totals.totalReceivableThisYear()).isEqualByComparingTo("230.00");
        assertThat(totals.totalPayableOpen()).isEqualByComparingTo("75.00");
        assertThat(totals.totalPayableOverdue()).isEqualByComparingTo("0");
        assertThat(totals.totalPayableThisYear()).isEqualByComparingTo("375.00");
    }

    @Test
    void overdueRequiresPastDueDate() {
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmount(new BigDecimal("80.00"));
            d.setDueDate(daysAgo(5));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmount(new BigDecimal("20.00"));
            d.setDueDate(daysAhead(5));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmount(new BigDecimal("17.00"));
            // due_date NULL — should not count as overdue
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmount(new BigDecimal("25.00"));
            d.setDueDate(daysAgo(3));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmount(new BigDecimal("500.00"));
            d.setDueDate(daysAgo(15));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmount(new BigDecimal("60.00"));
            d.setDueDate(daysAgo(1));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmount(new BigDecimal("33.00"));
            d.setDueDate(daysAhead(20));
        });
        flush();

        TotalsDto totals = documentRepository.totalsByOwner(OWNER);

        assertThat(totals.totalPayableOpen()).isEqualByComparingTo("92.00");
        assertThat(totals.totalPayableOverdue()).isEqualByComparingTo("55.00");
        assertThat(totals.totalReceivableOpen()).isEqualByComparingTo("473.00");
        assertThat(totals.totalReceivableOverdue()).isEqualByComparingTo("440.00");
    }

    @Test
    void thisYearFilterIsSargableAndExcludesPriorAndFutureYears() {
        // Paid last-year invoice: excluded from everything
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmount(new BigDecimal("500.00"));
            d.setIssueDate(lastYear());
            d.setPaidOn(Instant.now());
        });
        // Unpaid last-year invoice: still appears in Open/Overdue, but NOT ThisYear
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmount(new BigDecimal("800.00"));
            d.setIssueDate(lastYear());
            d.setDueDate(daysAgo(200));
        });
        // Current-year rows
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmount(new BigDecimal("60.00"));
            d.setIssueDate(Instant.now());
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmount(new BigDecimal("10.00"));
            d.setIssueDate(Instant.now());
        });
        // NULL issue_date must not count in ThisYear
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmount(new BigDecimal("123.00"));
            d.setIssueDate(null);
        });
        flush();

        TotalsDto totals = documentRepository.totalsByOwner(OWNER);

        assertThat(totals.totalReceivableThisYear()).isEqualByComparingTo("50.00");
        assertThat(totals.totalPayableThisYear()).isEqualByComparingTo("0");
        assertThat(totals.totalPayableOpen()).isEqualByComparingTo("800.00");
        assertThat(totals.totalPayableOverdue()).isEqualByComparingTo("800.00");
        assertThat(totals.totalReceivableOpen()).isEqualByComparingTo("173.00");
    }

    @Test
    void otherOwnersAreFullyIsolated() {
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmount(new BigDecimal("100.00"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmount(new BigDecimal("200.00"));
            d.setDueDate(daysAgo(10));
        });
        persistOther(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmount(new BigDecimal("9999.00"));
        });
        persistOther(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmount(new BigDecimal("555.00"));
        });
        persistOther(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmount(new BigDecimal("12345.00"));
            d.setDueDate(daysAgo(50));
        });
        flush();

        TotalsDto ownerTotals = documentRepository.totalsByOwner(OWNER);
        assertThat(ownerTotals.totalPayableOpen()).isEqualByComparingTo("100.00");
        assertThat(ownerTotals.totalReceivableOpen()).isEqualByComparingTo("200.00");
        assertThat(ownerTotals.totalReceivableOverdue()).isEqualByComparingTo("200.00");

        TotalsDto otherTotals = documentRepository.totalsByOwner(OTHER_OWNER);
        assertThat(otherTotals.totalPayableOpen()).isEqualByComparingTo("9444.00");
        assertThat(otherTotals.totalReceivableOpen()).isEqualByComparingTo("12345.00");
        assertThat(otherTotals.totalReceivableOverdue()).isEqualByComparingTo("12345.00");
    }

    @Test
    void nullAmountIsTreatedAsZero() {
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmount(null);
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmount(new BigDecimal("42.00"));
        });
        flush();

        TotalsDto totals = documentRepository.totalsByOwner(OWNER);

        assertThat(totals.totalPayableOpen()).isEqualByComparingTo("42.00");
        assertThat(totals.totalPayableThisYear()).isEqualByComparingTo("42.00");
    }

    @Test
    void comprehensiveMixedScenario() {
        // ----- Owner, current year, incoming -----
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.INVOICE);
            d.setAmount(new BigDecimal("100.00"));
            d.setDueDate(daysAgo(5));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.INVOICE);
            d.setAmount(new BigDecimal("250.50"));
            d.setDueDate(daysAhead(10));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.INVOICE);
            d.setAmount(new BigDecimal("75.00"));
            d.setPaidOn(Instant.now());
            d.setDueDate(daysAgo(30));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmount(new BigDecimal("50.00"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmount(new BigDecimal("30.00"));
            d.setDueDate(daysAgo(2));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.INVOICE);
            d.setAmount(new BigDecimal("999.99"));
            d.setDraftedOn(Instant.now());
        });

        // ----- Owner, current year, outgoing -----
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.INVOICE);
            d.setAmount(new BigDecimal("500.00"));
            d.setDueDate(daysAgo(15));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.INVOICE);
            d.setAmount(new BigDecimal("123.45"));
            d.setDueDate(daysAhead(10));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.INVOICE);
            d.setAmount(new BigDecimal("200.00"));
            d.setPaidOn(Instant.now());
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmount(new BigDecimal("45.00"));
            d.setCurrency(USD);
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmount(new BigDecimal("10.00"));
            d.setPaidOn(Instant.now());
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.INVOICE);
            d.setAmount(new BigDecimal("42.00"));
            d.setDraftedOn(Instant.now());
        });

        // ----- Owner, prior year -----
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.INVOICE);
            d.setAmount(new BigDecimal("800.00"));
            d.setIssueDate(lastYear());
            d.setDueDate(daysAgo(380));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.INVOICE);
            d.setAmount(new BigDecimal("1000.00"));
            d.setIssueDate(lastYear());
            d.setPaidOn(daysAgo(300));
        });

        // ----- Other owner: must be entirely excluded -----
        persistOther(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.INVOICE);
            d.setAmount(new BigDecimal("10000.00"));
            d.setDueDate(daysAgo(50));
        });
        persistOther(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmount(new BigDecimal("2500.00"));
        });
        flush();

        TotalsDto totals = documentRepository.totalsByOwner(OWNER);

        // 100 + 250.50 - 50 - 30 + 800 = 1070.50
        assertThat(totals.totalPayableOpen()).isEqualByComparingTo("1070.50");
        // 100 (overdue invoice) - 30 (overdue credit note) + 800 (prior-year overdue) = 870.00
        assertThat(totals.totalPayableOverdue()).isEqualByComparingTo("870.00");
        // 100 + 250.50 + 75 (paid still counts) - 50 - 30 = 345.50  (drafted + last-year excluded)
        assertThat(totals.totalPayableThisYear()).isEqualByComparingTo("345.50");
        // 500 + 123.45 - 45 = 578.45  (paid invoice + paid credit note + drafted excluded)
        assertThat(totals.totalReceivableOpen()).isEqualByComparingTo("578.45");
        // Only the 500 invoice is both unpaid and past due
        assertThat(totals.totalReceivableOverdue()).isEqualByComparingTo("500.00");
        // 500 + 123.45 + 200 - 45 - 10 = 768.45  (drafted + last-year excluded)
        assertThat(totals.totalReceivableThisYear()).isEqualByComparingTo("768.45");
    }
}

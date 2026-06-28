package org.letspeppol.app.repository;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letspeppol.app.PostgresIntegrationTest;
import org.letspeppol.app.dto.TotalsRow;
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
        d.setAmountInclVat(new BigDecimal("100.00"));
        d.setIssueDate(Instant.now());
        d.setCreatedExternally(false);
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
        persistOther(d -> d.setAmountInclVat(new BigDecimal("500.00")));
        flush();

        TotalsRow totals = documentRepository.totalsByOwner(OWNER);

        assertThat(totals.totalPayableOpenInclVat()).isEqualByComparingTo("0");
        assertThat(totals.totalPayableOverdueInclVat()).isEqualByComparingTo("0");
        assertThat(totals.totalPayableThisYearInclVat()).isEqualByComparingTo("0");
        assertThat(totals.totalReceivableOpenInclVat()).isEqualByComparingTo("0");
        assertThat(totals.totalReceivableOverdueInclVat()).isEqualByComparingTo("0");
        assertThat(totals.totalReceivableThisYearInclVat()).isEqualByComparingTo("0");
    }

    @Test
    void incomingCreditNoteSubtractsFromPayableTotals() {
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.INVOICE);
            d.setAmountInclVat(new BigDecimal("100.00"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.INVOICE);
            d.setAmountInclVat(new BigDecimal("49.50"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmountInclVat(new BigDecimal("30.00"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmountInclVat(new BigDecimal("12.50"));
        });
        // Noise: drafted credit note must NOT subtract
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmountInclVat(new BigDecimal("777.00"));
            d.setDraftedOn(Instant.now());
        });
        // Noise: outgoing credit note must not touch payable totals
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmountInclVat(new BigDecimal("400.00"));
        });
        // Noise: other owner's credit note must not subtract
        persistOther(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmountInclVat(new BigDecimal("999.00"));
        });
        flush();

        TotalsRow totals = documentRepository.totalsByOwner(OWNER);

        assertThat(totals.totalPayableOpenInclVat()).isEqualByComparingTo("107.00");
        assertThat(totals.totalPayableThisYearInclVat()).isEqualByComparingTo("107.00");
    }

    @Test
    void outgoingCreditNoteSubtractsFromReceivableTotals() {
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.INVOICE);
            d.setAmountInclVat(new BigDecimal("250.00"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.INVOICE);
            d.setAmountInclVat(new BigDecimal("125.75"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmountInclVat(new BigDecimal("40.00"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmountInclVat(new BigDecimal("15.25"));
        });
        // Noise: incoming invoice/credit note must not shift receivables
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.INVOICE);
            d.setAmountInclVat(new BigDecimal("600.00"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmountInclVat(new BigDecimal("60.00"));
        });
        flush();

        TotalsRow totals = documentRepository.totalsByOwner(OWNER);

        assertThat(totals.totalReceivableOpenInclVat()).isEqualByComparingTo("320.50");
        assertThat(totals.totalReceivableThisYearInclVat()).isEqualByComparingTo("320.50");
    }

    @Test
    void draftsAreExcluded() {
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmountInclVat(new BigDecimal("100.00"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmountInclVat(new BigDecimal("999.00"));
            d.setDraftedOn(Instant.now());
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmountInclVat(new BigDecimal("888.00"));
            d.setDraftedOn(Instant.now().minus(1, ChronoUnit.DAYS));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmountInclVat(new BigDecimal("777.00"));
            d.setDraftedOn(Instant.now());
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmountInclVat(new BigDecimal("42.00"));
        });
        flush();

        TotalsRow totals = documentRepository.totalsByOwner(OWNER);

        assertThat(totals.totalPayableOpenInclVat()).isEqualByComparingTo("100.00");
        assertThat(totals.totalPayableThisYearInclVat()).isEqualByComparingTo("100.00");
        assertThat(totals.totalReceivableOpenInclVat()).isEqualByComparingTo("42.00");
        assertThat(totals.totalReceivableThisYearInclVat()).isEqualByComparingTo("42.00");
    }

    @Test
    void paidDocumentsAreExcludedFromOpenButCountInThisYear() {
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmountInclVat(new BigDecimal("200.00"));
            d.setPaidOn(Instant.now());
            d.setDueDate(daysAgo(30));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmountInclVat(new BigDecimal("50.00"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmountInclVat(new BigDecimal("20.00"));
            d.setPaidOn(Instant.now());
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmountInclVat(new BigDecimal("300.00"));
            d.setPaidOn(Instant.now());
            d.setDueDate(daysAgo(10));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmountInclVat(new BigDecimal("75.00"));
        });
        flush();

        TotalsRow totals = documentRepository.totalsByOwner(OWNER);

        assertThat(totals.totalReceivableOpenInclVat()).isEqualByComparingTo("50.00");
        assertThat(totals.totalReceivableOverdueInclVat()).isEqualByComparingTo("0");
        assertThat(totals.totalReceivableThisYearInclVat()).isEqualByComparingTo("230.00");
        assertThat(totals.totalPayableOpenInclVat()).isEqualByComparingTo("75.00");
        assertThat(totals.totalPayableOverdueInclVat()).isEqualByComparingTo("0");
        assertThat(totals.totalPayableThisYearInclVat()).isEqualByComparingTo("375.00");
    }

    @Test
    void overdueRequiresPastDueDate() {
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmountInclVat(new BigDecimal("80.00"));
            d.setDueDate(daysAgo(5));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmountInclVat(new BigDecimal("20.00"));
            d.setDueDate(daysAhead(5));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmountInclVat(new BigDecimal("17.00"));
            // due_date NULL — should not count as overdue
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmountInclVat(new BigDecimal("25.00"));
            d.setDueDate(daysAgo(3));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmountInclVat(new BigDecimal("500.00"));
            d.setDueDate(daysAgo(15));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmountInclVat(new BigDecimal("60.00"));
            d.setDueDate(daysAgo(1));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmountInclVat(new BigDecimal("33.00"));
            d.setDueDate(daysAhead(20));
        });
        flush();

        TotalsRow totals = documentRepository.totalsByOwner(OWNER);

        assertThat(totals.totalPayableOpenInclVat()).isEqualByComparingTo("92.00");
        assertThat(totals.totalPayableOverdueInclVat()).isEqualByComparingTo("55.00");
        assertThat(totals.totalReceivableOpenInclVat()).isEqualByComparingTo("473.00");
        assertThat(totals.totalReceivableOverdueInclVat()).isEqualByComparingTo("440.00");
    }

    @Test
    void thisYearFilterIsSargableAndExcludesPriorAndFutureYears() {
        // Paid last-year invoice: excluded from everything
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmountInclVat(new BigDecimal("500.00"));
            d.setIssueDate(lastYear());
            d.setPaidOn(Instant.now());
        });
        // Unpaid last-year invoice: still appears in Open/Overdue, but NOT ThisYear
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmountInclVat(new BigDecimal("800.00"));
            d.setIssueDate(lastYear());
            d.setDueDate(daysAgo(200));
        });
        // Current-year rows
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmountInclVat(new BigDecimal("60.00"));
            d.setIssueDate(Instant.now());
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmountInclVat(new BigDecimal("10.00"));
            d.setIssueDate(Instant.now());
        });
        // NULL issue_date must not count in ThisYear
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmountInclVat(new BigDecimal("123.00"));
            d.setIssueDate(null);
        });
        flush();

        TotalsRow totals = documentRepository.totalsByOwner(OWNER);

        assertThat(totals.totalReceivableThisYearInclVat()).isEqualByComparingTo("50.00");
        assertThat(totals.totalPayableThisYearInclVat()).isEqualByComparingTo("0");
        assertThat(totals.totalPayableOpenInclVat()).isEqualByComparingTo("800.00");
        assertThat(totals.totalPayableOverdueInclVat()).isEqualByComparingTo("800.00");
        assertThat(totals.totalReceivableOpenInclVat()).isEqualByComparingTo("173.00");
    }

    @Test
    void otherOwnersAreFullyIsolated() {
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmountInclVat(new BigDecimal("100.00"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmountInclVat(new BigDecimal("200.00"));
            d.setDueDate(daysAgo(10));
        });
        persistOther(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmountInclVat(new BigDecimal("9999.00"));
        });
        persistOther(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmountInclVat(new BigDecimal("555.00"));
        });
        persistOther(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmountInclVat(new BigDecimal("12345.00"));
            d.setDueDate(daysAgo(50));
        });
        flush();

        TotalsRow ownerTotals = documentRepository.totalsByOwner(OWNER);
        assertThat(ownerTotals.totalPayableOpenInclVat()).isEqualByComparingTo("100.00");
        assertThat(ownerTotals.totalReceivableOpenInclVat()).isEqualByComparingTo("200.00");
        assertThat(ownerTotals.totalReceivableOverdueInclVat()).isEqualByComparingTo("200.00");

        TotalsRow otherTotals = documentRepository.totalsByOwner(OTHER_OWNER);
        assertThat(otherTotals.totalPayableOpenInclVat()).isEqualByComparingTo("9444.00");
        assertThat(otherTotals.totalReceivableOpenInclVat()).isEqualByComparingTo("12345.00");
        assertThat(otherTotals.totalReceivableOverdueInclVat()).isEqualByComparingTo("12345.00");
    }

    @Test
    void nullAmountIsTreatedAsZero() {
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmountInclVat(null);
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmountInclVat(new BigDecimal("42.00"));
        });
        flush();

        TotalsRow totals = documentRepository.totalsByOwner(OWNER);

        assertThat(totals.totalPayableOpenInclVat()).isEqualByComparingTo("42.00");
        assertThat(totals.totalPayableThisYearInclVat()).isEqualByComparingTo("42.00");
    }

    @Test
    void exclVatTotalsAggregateIndependentlyFromInclVat() {
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.INVOICE);
            d.setAmountInclVat(new BigDecimal("121.00"));
            d.setAmountExclVat(new BigDecimal("100.00"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmountInclVat(new BigDecimal("24.20"));
            d.setAmountExclVat(new BigDecimal("20.00"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.INVOICE);
            d.setAmountInclVat(new BigDecimal("60.50"));
            d.setAmountExclVat(new BigDecimal("50.00"));
            d.setDueDate(daysAgo(3));
        });
        persist(d -> {
            // Legacy row: no excl_vat backfill possible.
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.INVOICE);
            d.setAmountInclVat(new BigDecimal("999.99"));
            d.setAmountExclVat(null);
        });
        flush();

        TotalsRow totals = documentRepository.totalsByOwner(OWNER);

        assertThat(totals.totalPayableOpenInclVat()).isEqualByComparingTo("96.80");
        assertThat(totals.totalPayableOpenExclVat()).isEqualByComparingTo("80.00");
        assertThat(totals.totalReceivableOpenInclVat()).isEqualByComparingTo("1060.49");
        assertThat(totals.totalReceivableOpenExclVat()).isEqualByComparingTo("50.00");
        assertThat(totals.totalReceivableOverdueExclVat()).isEqualByComparingTo("50.00");
    }

    @Test
    void comprehensiveMixedScenario() {
        // ----- Owner, current year, incoming -----
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.INVOICE);
            d.setAmountInclVat(new BigDecimal("100.00"));
            d.setDueDate(daysAgo(5));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.INVOICE);
            d.setAmountInclVat(new BigDecimal("250.50"));
            d.setDueDate(daysAhead(10));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.INVOICE);
            d.setAmountInclVat(new BigDecimal("75.00"));
            d.setPaidOn(Instant.now());
            d.setDueDate(daysAgo(30));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmountInclVat(new BigDecimal("50.00"));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmountInclVat(new BigDecimal("30.00"));
            d.setDueDate(daysAgo(2));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.INVOICE);
            d.setAmountInclVat(new BigDecimal("999.99"));
            d.setDraftedOn(Instant.now());
        });

        // ----- Owner, current year, outgoing -----
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.INVOICE);
            d.setAmountInclVat(new BigDecimal("500.00"));
            d.setDueDate(daysAgo(15));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.INVOICE);
            d.setAmountInclVat(new BigDecimal("123.45"));
            d.setDueDate(daysAhead(10));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.INVOICE);
            d.setAmountInclVat(new BigDecimal("200.00"));
            d.setPaidOn(Instant.now());
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmountInclVat(new BigDecimal("45.00"));
            d.setCurrency(USD);
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmountInclVat(new BigDecimal("10.00"));
            d.setPaidOn(Instant.now());
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.INVOICE);
            d.setAmountInclVat(new BigDecimal("42.00"));
            d.setDraftedOn(Instant.now());
        });

        // ----- Owner, prior year -----
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.INVOICE);
            d.setAmountInclVat(new BigDecimal("800.00"));
            d.setIssueDate(lastYear());
            d.setDueDate(daysAgo(380));
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.INVOICE);
            d.setAmountInclVat(new BigDecimal("1000.00"));
            d.setIssueDate(lastYear());
            d.setPaidOn(daysAgo(300));
        });

        // ----- Other owner: must be entirely excluded -----
        persistOther(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setType(DocumentType.INVOICE);
            d.setAmountInclVat(new BigDecimal("10000.00"));
            d.setDueDate(daysAgo(50));
        });
        persistOther(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setType(DocumentType.CREDIT_NOTE);
            d.setAmountInclVat(new BigDecimal("2500.00"));
        });
        flush();

        TotalsRow totals = documentRepository.totalsByOwner(OWNER);

        // 100 + 250.50 - 50 - 30 + 800 = 1070.50
        assertThat(totals.totalPayableOpenInclVat()).isEqualByComparingTo("1070.50");
        // 100 (overdue invoice) - 30 (overdue credit note) + 800 (prior-year overdue) = 870.00
        assertThat(totals.totalPayableOverdueInclVat()).isEqualByComparingTo("870.00");
        // 100 + 250.50 + 75 (paid still counts) - 50 - 30 = 345.50  (drafted + last-year excluded)
        assertThat(totals.totalPayableThisYearInclVat()).isEqualByComparingTo("345.50");
        // 500 + 123.45 - 45 = 578.45  (paid invoice + paid credit note + drafted excluded)
        assertThat(totals.totalReceivableOpenInclVat()).isEqualByComparingTo("578.45");
        // Only the 500 invoice is both unpaid and past due
        assertThat(totals.totalReceivableOverdueInclVat()).isEqualByComparingTo("500.00");
        // 500 + 123.45 + 200 - 45 - 10 = 768.45  (drafted + last-year excluded)
        assertThat(totals.totalReceivableThisYearInclVat()).isEqualByComparingTo("768.45");
    }

    @Test
    void erroredInvoicesAreExcludedFromAllMoneyTotals() {
        // Issue #269: a valid pending outgoing invoice still counts in open/overdue/this-year
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmountInclVat(new BigDecimal("50.00"));
            d.setDueDate(daysAgo(5));
        });
        // Errored outgoing invoice (processedStatus set): excluded everywhere even though unpaid, non-draft, overdue, this year
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setAmountInclVat(new BigDecimal("65.00"));
            d.setDueDate(daysAgo(5));
            d.setProcessedStatus("BUSINESS_ERROR: rejected by recipient AP");
        });
        // Errored incoming invoice: excluded from payable totals too
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setAmountInclVat(new BigDecimal("77.00"));
            d.setDueDate(daysAgo(5));
            d.setProcessedStatus("BUSINESS_ERROR");
        });
        flush();

        TotalsRow totals = documentRepository.totalsByOwner(OWNER);

        assertThat(totals.totalReceivableOpenInclVat()).isEqualByComparingTo("50.00");
        assertThat(totals.totalReceivableOverdueInclVat()).isEqualByComparingTo("50.00");
        assertThat(totals.totalReceivableThisYearInclVat()).isEqualByComparingTo("50.00");
        assertThat(totals.totalPayableOpenInclVat()).isEqualByComparingTo("0");
        assertThat(totals.totalPayableOverdueInclVat()).isEqualByComparingTo("0");
    }

    @Test
    void erroredUnseenCountCountsOnlyUnacknowledgedErroredDocuments() {
        // Errored, not yet acknowledged -> counted
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setProcessedStatus("ERR-A");
        });
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setProcessedStatus("ERR-B");
        });
        // Errored but acknowledged (errorSeenOn set) -> NOT counted
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setProcessedStatus("ERR-C");
            d.setErrorSeenOn(Instant.now());
        });
        // Valid document (no processedStatus) -> NOT counted
        persist(d -> d.setDirection(DocumentDirection.OUTGOING));
        // Drafted errored document -> excluded by the drafted_on IS NULL subquery filter
        persist(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setProcessedStatus("ERR-D");
            d.setDraftedOn(Instant.now());
        });
        // Incoming document carrying a status is not an outgoing send error -> NOT counted (#270 review)
        persist(d -> {
            d.setDirection(DocumentDirection.INCOMING);
            d.setProcessedStatus("ERR-INCOMING");
        });
        // Other owner's errored document -> isolated
        persistOther(d -> {
            d.setDirection(DocumentDirection.OUTGOING);
            d.setProcessedStatus("ERR-OTHER");
        });
        flush();

        assertThat(documentRepository.totalsByOwner(OWNER).erroredUnseenCount()).isEqualTo(2L);
        assertThat(documentRepository.totalsByOwner(OTHER_OWNER).erroredUnseenCount()).isEqualTo(1L);
    }
}

package db.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letspeppol.app.PostgresIntegrationTest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bypasses JPA so the `ubl` text column can hold either raw XML or an OID pointer —
 * both shapes occur in production and the migration must cope with either.
 */
class V1_11BackfillMigrationTest extends PostgresIntegrationTest {

    private static final String VALID_UBL = """
            <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                     xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                     xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                <cbc:ID>INV-1</cbc:ID>
                <cbc:IssueDate>2026-01-15</cbc:IssueDate>
                <cbc:DocumentCurrencyCode>EUR</cbc:DocumentCurrencyCode>
                <cac:AccountingSupplierParty>
                    <cac:Party>
                        <cbc:EndpointID schemeID="0208">0123456789</cbc:EndpointID>
                        <cac:PartyName><cbc:Name>Supplier Ltd</cbc:Name></cac:PartyName>
                    </cac:Party>
                </cac:AccountingSupplierParty>
                <cac:AccountingCustomerParty>
                    <cac:Party>
                        <cbc:EndpointID schemeID="0208">9876543210</cbc:EndpointID>
                        <cac:PartyName><cbc:Name>Customer Ltd</cbc:Name></cac:PartyName>
                    </cac:Party>
                </cac:AccountingCustomerParty>
                <cac:LegalMonetaryTotal>
                    <cbc:TaxExclusiveAmount currencyID="EUR">82.64</cbc:TaxExclusiveAmount>
                    <cbc:PayableAmount currencyID="EUR">100.00</cbc:PayableAmount>
                </cac:LegalMonetaryTotal>
            </Invoice>""";

    @BeforeAll
    static void runFlyway() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("app")
                .defaultSchema("app")
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @BeforeEach
    void cleanData() throws SQLException {
        try (Connection c = connect()) {
            c.createStatement().execute("TRUNCATE app.document, app.company RESTART IDENTITY CASCADE");
        }
    }

    @Test
    void backfillsBothAmountsWhenUblStoredAsRawText() throws Exception {
        UUID docId;
        try (Connection c = connect()) {
            long companyId = insertCompany(c, "0208:0000000001");
            docId = insertDocumentWithRawXmlUbl(c, companyId, "0208:0000000001", VALID_UBL);
        }

        runMigration();

        assertThat(amountInclVat(docId)).isEqualByComparingTo("100.00");
        assertThat(amountExclVat(docId)).isEqualByComparingTo("82.64");
    }

    @Test
    void backfillsBothAmountsWhenUblStoredAsOidPointer() throws Exception {
        UUID docId;
        try (Connection c = connect()) {
            long companyId = insertCompany(c, "0208:0000000002");
            docId = insertDocumentWithOidUbl(c, companyId, "0208:0000000002", VALID_UBL);
        }

        runMigration();

        assertThat(amountInclVat(docId)).isEqualByComparingTo("100.00");
        assertThat(amountExclVat(docId)).isEqualByComparingTo("82.64");
    }

    @Test
    void skipsRowsWithNullUbl() throws Exception {
        UUID docId;
        try (Connection c = connect()) {
            long companyId = insertCompany(c, "0208:0000000003");
            docId = insertDocumentWithRawXmlUbl(c, companyId, "0208:0000000003", null);
        }

        runMigration();

        assertThat(amountInclVat(docId)).isNull();
        assertThat(amountExclVat(docId)).isNull();
    }

    @Test
    void doesNotFailWhenSomeRowsHaveMalformedUbl() throws Exception {
        UUID badDocId;
        UUID goodDocId;
        try (Connection c = connect()) {
            long companyId = insertCompany(c, "0208:0000000004");
            badDocId = insertDocumentWithRawXmlUbl(c, companyId, "0208:0000000004", "<garbage>not valid ubl</garbage>");
            goodDocId = insertDocumentWithRawXmlUbl(c, companyId, "0208:0000000004", VALID_UBL);
        }

        runMigration();

        assertThat(amountInclVat(badDocId)).isNull();
        assertThat(amountExclVat(badDocId)).isNull();
        assertThat(amountInclVat(goodDocId)).isEqualByComparingTo("100.00");
        assertThat(amountExclVat(goodDocId)).isEqualByComparingTo("82.64");
    }

    private static void runMigration() throws Exception {
        try (Connection c = connect()) {
            c.setAutoCommit(false);
            new V1_11__backfill_amounts().migrate(stubContext(c));
            c.commit();
        }
    }

    private static Context stubContext(Connection c) {
        return new Context() {
            @Override public Configuration getConfiguration() { return null; }
            @Override public Connection getConnection() { return c; }
        };
    }

    private static Connection connect() throws SQLException {
        Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        c.createStatement().execute("SET search_path = app");
        return c;
    }

    private static long insertCompany(Connection c, String peppolId) throws SQLException {
        try (PreparedStatement addr = c.prepareStatement(
                "INSERT INTO app.address (city, country_code, postal_code, street) VALUES (?, ?, ?, ?) RETURNING id")) {
            addr.setString(1, "Brussels");
            addr.setString(2, "BE");
            addr.setString(3, "1000");
            addr.setString(4, "Rue de Test 1");
            try (ResultSet rs = addr.executeQuery()) {
                rs.next();
                long addressId = rs.getLong(1);
                try (PreparedStatement co = c.prepareStatement(
                        "INSERT INTO app.company (peppol_id, vat_number, name, subscriber, subscriber_email, registered_office_id) "
                                + "VALUES (?, ?, ?, ?, ?, ?) RETURNING id")) {
                    co.setString(1, peppolId);
                    co.setString(2, "BE0123456789");
                    co.setString(3, "Name " + peppolId);
                    co.setString(4, "Sub");
                    co.setString(5, "sub@example.com");
                    co.setLong(6, addressId);
                    try (ResultSet coRs = co.executeQuery()) {
                        coRs.next();
                        return coRs.getLong(1);
                    }
                }
            }
        }
    }

    private static UUID insertDocumentWithRawXmlUbl(Connection c, long companyId, String ownerPeppolId, String ublXml) throws SQLException {
        UUID id = UUID.randomUUID();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO app.document (id, direction, owner_peppol_id, partner_peppol_id, ubl, company_id, "
                        + "partner_name, invoice_reference, currency, type, created_externally, amount_incl_vat, amount_excl_vat) "
                        + "VALUES (?, ?::document_direction, ?, ?, ?, ?, ?, ?, ?, ?::document_type, ?, NULL, NULL)")) {
            ps.setObject(1, id);
            ps.setString(2, "INCOMING");
            ps.setString(3, ownerPeppolId);
            ps.setString(4, "0208:9999999999");
            ps.setString(5, ublXml);
            ps.setLong(6, companyId);
            ps.setString(7, "Supplier Ltd");
            ps.setString(8, "INV-" + id);
            ps.setString(9, "EUR");
            ps.setString(10, "INVOICE");
            ps.setBoolean(11, true);
            ps.executeUpdate();
        }
        return id;
    }

    private static UUID insertDocumentWithOidUbl(Connection c, long companyId, String ownerPeppolId, String ublXml) throws SQLException {
        // pg_largeobject operations require an active transaction.
        boolean prev = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            long oid;
            try (PreparedStatement ps = c.prepareStatement("SELECT lo_from_bytea(0, ?)")) {
                ps.setBytes(1, ublXml.getBytes(StandardCharsets.UTF_8));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    oid = rs.getLong(1);
                }
            }
            UUID id = insertDocumentWithRawXmlUbl(c, companyId, ownerPeppolId, Long.toString(oid));
            c.commit();
            return id;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(prev);
        }
    }

    private static BigDecimal amountInclVat(UUID id) throws SQLException {
        return queryAmount(id, "amount_incl_vat");
    }

    private static BigDecimal amountExclVat(UUID id) throws SQLException {
        return queryAmount(id, "amount_excl_vat");
    }

    private static BigDecimal queryAmount(UUID id, String column) throws SQLException {
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT " + column + " FROM app.document WHERE id = ?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBigDecimal(1);
            }
        }
    }
}

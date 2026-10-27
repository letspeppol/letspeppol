package org.letspeppol.kyc;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyUsersMigrationTest {

    private static final String APP_ACCOUNT_EXTERNAL_ID = "b095630d-1bf3-4250-bf9e-2d49e6ce505b";
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static long admin;
    private static long app;
    private static long linkedBeforeOwnerships;
    private static long linkedTwice;
    private static long legacyRoles;
    private static long user;
    private static long userRow;
    private static long drafter;

    @BeforeAll
    static void migrateLegacyAppLinks() throws SQLException {
        POSTGRES.start();
        migrate("1.9");
        try (Connection connection = connect()) {
            linkedBeforeOwnerships = insertCompany(connection, "0208:0000000001");
            linkedTwice = insertCompany(connection, "0208:0000000002");
            admin = insertAccount(connection, UUID.randomUUID().toString());
            app = insertAccount(connection, APP_ACCOUNT_EXTERNAL_ID);
            insertOwnership(connection, admin, linkedBeforeOwnerships, "ADMIN");
            insertOwnership(connection, app, linkedBeforeOwnerships, "ADMIN");
            insertOwnership(connection, app, linkedTwice, "ADMIN");
            insertOwnership(connection, app, linkedTwice, "APP");

            legacyRoles = insertCompany(connection, "0208:0000000003");
            user = insertAccount(connection, UUID.randomUUID().toString());
            drafter = insertAccount(connection, UUID.randomUUID().toString());
            long readRow = insertOwnership(connection, user, legacyRoles, "USER_READ");
            insertOwnership(connection, user, legacyRoles, "USER_DRAFT");
            userRow = insertOwnership(connection, user, legacyRoles, "USER");
            insertEmailVerification(connection, readRow, "requested-through-the-read-role");
            insertOwnership(connection, drafter, legacyRoles, "USER_DRAFT");
        }
        migrate("1.10");
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    void notificationAppLinkedAsAdminBecomesAnApp() throws SQLException {
        assertThat(types(app, linkedBeforeOwnerships)).containsExactly("APP");
    }

    @Test
    void notificationAppLinkedTwiceKeepsOnlyItsAppOwnership() throws SQLException {
        assertThat(types(app, linkedTwice)).containsExactly("APP");
    }

    @Test
    void realAdminIsUntouched() throws SQLException {
        assertThat(types(admin, linkedBeforeOwnerships)).containsExactly("ADMIN");
    }

    @Test
    void legacyRolesOfOneAccountMergeIntoOneUserWithTheWidestMask() throws SQLException {
        assertThat(types(user, legacyRoles)).containsExactly("USER");
        assertThat(permissionMasks(user, legacyRoles)).containsExactly(255);
    }

    @Test
    void verificationRequestedThroughAMergedRowFollowsTheKeptRow() throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT requester_ownership_id FROM email_verification WHERE token = 'requested-through-the-read-role'")) {
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getLong(1)).isEqualTo(userRow);
            }
        }
    }

    @Test
    void draftRoleKeepsReadingDraftingAndPaymentStatus() throws SQLException {
        assertThat(permissionMasks(drafter, legacyRoles)).containsExactly(11);
    }

    private static void migrate(String target) {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("kyc")
                .defaultSchema("kyc")
                .locations("classpath:db/migration")
                .placeholders(Map.of("app-account-external-id", APP_ACCOUNT_EXTERNAL_ID))
                .target(target)
                .load()
                .migrate();
    }

    private static Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        connection.setSchema("kyc");
        return connection;
    }

    private static long insertCompany(Connection connection, String peppolId) throws SQLException {
        return insert(connection,
                "INSERT INTO company (peppol_id, name, registered_on_peppol) VALUES (?, 'Company', true) RETURNING id",
                peppolId);
    }

    private static long insertAccount(Connection connection, String externalId) throws SQLException {
        return insert(connection,
                "INSERT INTO account (name, email, created_on, verified, external_id) VALUES ('Account', ?, now(), true, ?::uuid) RETURNING id",
                externalId + "@example.com", externalId);
    }

    private static long insertOwnership(Connection connection, long accountId, long companyId, String type) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO ownership (account_id, company_id, type, created_on, last_used) VALUES (?, ?, ?::account_type, now(), now()) RETURNING id")) {
            statement.setLong(1, accountId);
            statement.setLong(2, companyId);
            statement.setString(3, type);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void insertEmailVerification(Connection connection, long requesterOwnershipId, String token) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO email_verification (email, peppol_id, token, verified, expires_on, created_on, requester_ownership_id, type) "
                        + "VALUES ('invitee@example.com', '0208:0000000003', ?, false, now() + interval '1 day', now(), ?, 'USER_READ')")) {
            statement.setString(1, token);
            statement.setLong(2, requesterOwnershipId);
            statement.executeUpdate();
        }
    }

    private static long insert(Connection connection, String sql, String... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setString(i + 1, values[i]);
            }
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static List<Integer> permissionMasks(long accountId, long companyId) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT permission_mask FROM ownership WHERE account_id = ? AND company_id = ?")) {
            statement.setLong(1, accountId);
            statement.setLong(2, companyId);
            List<Integer> masks = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    masks.add(result.getInt(1));
                }
            }
            return masks;
        }
    }

    private static List<String> types(long accountId, long companyId) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT type::text FROM ownership WHERE account_id = ? AND company_id = ? ORDER BY type")) {
            statement.setLong(1, accountId);
            statement.setLong(2, companyId);
            List<String> types = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    types.add(result.getString(1));
                }
            }
            return types;
        }
    }
}

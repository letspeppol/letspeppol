package db.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class V1_13PartnerTimesheetMigrationTest {

    @Test
    void addsDisabledNonNullTimesheetToExistingAndNewPartners() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:partner-timesheet;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1")) {
            connection.createStatement().execute("DROP ALL OBJECTS");
            connection.createStatement().execute("CREATE TABLE partner (id bigint PRIMARY KEY)");
            connection.createStatement().execute("INSERT INTO partner (id) VALUES (1)");

            ScriptUtils.executeSqlScript(
                    connection,
                    new ClassPathResource("db/migration/V1.13__partner_timesheet.sql")
            );

            assertThat(timesheetFor(connection, 1)).isFalse();
            connection.createStatement().execute("INSERT INTO partner (id) VALUES (2)");
            assertThat(timesheetFor(connection, 2)).isFalse();
            assertThatThrownBy(() -> connection.createStatement().execute(
                    "INSERT INTO partner (id, timesheet) VALUES (3, NULL)"
            )).isInstanceOf(SQLException.class);
        }
    }

    private boolean timesheetFor(Connection connection, long partnerId) throws SQLException {
        try (ResultSet result = connection.createStatement().executeQuery(
                "SELECT timesheet FROM partner WHERE id = " + partnerId
        )) {
            assertThat(result.next()).isTrue();
            return result.getBoolean("timesheet");
        }
    }
}

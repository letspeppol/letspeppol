package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.letspeppol.app.dto.UblDto;
import org.letspeppol.app.model.DocumentDirection;
import org.letspeppol.app.util.UblParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

/**
 * Backfills amount_incl_vat / amount_excl_vat by re-parsing the archived UBL.
 * Rows with ubl IS NULL (no-archive lifecycle) are unrecoverable and keep
 * amount_excl_vat at the legacy value while amount_incl_vat stays null.
 */
public class V1_11__backfill_amounts extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V1_11__backfill_amounts.class);

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        int scanned = 0;
        int updated = 0;
        int unchanged = 0;
        int failed = 0;

        // @Lob String on Postgres may be stored as either a large-object OID (numeric pointer
        // in the text column) or raw XML, depending on Hibernate version. Detect per row.
        try (Statement select = connection.createStatement();
             ResultSet rs = select.executeQuery(
                     "SELECT id, direction::text AS direction, "
                             + "CASE WHEN ubl ~ '^[0-9]+$' "
                             + "THEN convert_from(lo_get(ubl::oid), 'UTF8') "
                             + "ELSE ubl END AS ubl, "
                             + "amount_incl_vat, amount_excl_vat "
                             + "FROM document WHERE ubl IS NOT NULL");
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE document SET amount_incl_vat = ?, amount_excl_vat = ? WHERE id = ?")) {
            // Stream rather than buffer — archived UBLs can be tens of KB and there may be thousands.
            select.setFetchSize(100);

            while (rs.next()) {
                scanned++;
                UUID id = rs.getObject("id", UUID.class);
                String directionStr = rs.getString("direction");
                String ubl = rs.getString("ubl");
                BigDecimal storedIncl = rs.getBigDecimal("amount_incl_vat");
                BigDecimal storedExcl = rs.getBigDecimal("amount_excl_vat");

                try {
                    UblDto dto = UblParser.parse(DocumentDirection.valueOf(directionStr), ubl);
                    BigDecimal parsedIncl = dto.amountInclVat();
                    BigDecimal parsedExcl = dto.amountExclVat();
                    if (sameAmount(storedIncl, parsedIncl) && sameAmount(storedExcl, parsedExcl)) {
                        unchanged++;
                        continue;
                    }
                    update.setBigDecimal(1, parsedIncl);
                    update.setBigDecimal(2, parsedExcl);
                    update.setObject(3, id);
                    update.executeUpdate();
                    updated++;
                } catch (Exception e) {
                    failed++;
                    log.warn("Backfill skipped for document {}: {}", id, e.getMessage());
                }
            }
        }

        log.info("Backfill complete: scanned={}, updated={}, unchanged={}, failed={}",
                scanned, updated, unchanged, failed);
    }

    private static boolean sameAmount(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.compareTo(b) == 0;
    }
}

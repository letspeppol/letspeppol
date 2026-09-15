package org.letspeppol.kyc;

import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.service.OAuth2AuthorizationCleanupJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OAuth2AuthorizationCleanupJobTests {

    @Autowired
    OAuth2AuthorizationCleanupJob cleanupJob;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void purgeRemovesOnlyAuthorizationsOlderThanTheSessionCouldStillNeed() {
        insertAuthorization("expired", Instant.now().minus(10, ChronoUnit.HOURS));
        insertAuthorization("within-session", Instant.now().minus(7, ChronoUnit.HOURS));
        insertAuthorization("recent", Instant.now().minus(5, ChronoUnit.MINUTES));

        cleanupJob.purgeExpired();

        List<String> remaining = jdbcTemplate.queryForList(
                "SELECT id FROM oauth2_authorization WHERE id IN ('expired', 'within-session', 'recent')", String.class);
        assertThat(remaining).containsExactlyInAnyOrder("within-session", "recent");
    }

    private void insertAuthorization(String id, Instant issuedAt) {
        jdbcTemplate.update("""
                INSERT INTO oauth2_authorization (id, registered_client_id, principal_name, authorization_grant_type,
                    authorization_code_value, authorization_code_issued_at, access_token_value, access_token_issued_at)
                VALUES (?, 'ui', 'person@example.com', 'authorization_code', ?, ?, ?, ?)
                """, id, "code-" + id, Timestamp.from(issuedAt), "token-" + id, Timestamp.from(issuedAt.plusSeconds(1)));
    }
}

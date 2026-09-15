package org.letspeppol.kyc;

import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.service.OAuth2AuthorizationCleanupJob;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OAuth2AuthorizationCleanupJobTests {

    @Autowired
    OAuth2AuthorizationCleanupJob cleanupJob;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void authorizationsOfAPrincipalIdleLongerThanTheSessionArePurged() {
        String principal = principal();
        String older = insertAuthorization(principal, Duration.ofHours(12));
        String newest = insertAuthorization(principal, Duration.ofHours(10));

        cleanupJob.purgeExpired();

        assertThat(remaining(older, newest)).isEmpty();
    }

    @Test
    void authorizationsOfAPrincipalActiveWithinTheSessionAreKept() {
        String principal = principal();
        String withinSession = insertAuthorization(principal, Duration.ofHours(7));

        cleanupJob.purgeExpired();

        assertThat(remaining(withinSession)).containsExactly(withinSession);
    }

    @Test
    void recentActivityKeepsOlderAuthorizationsAnOpenTabMayStillUseForLogout() {
        String principal = principal();
        String idleTab = insertAuthorization(principal, Duration.ofHours(20));
        String activeTab = insertAuthorization(principal, Duration.ofMinutes(5));

        cleanupJob.purgeExpired();

        assertThat(remaining(idleTab, activeTab)).containsExactlyInAnyOrder(idleTab, activeTab);
    }

    @Test
    void authorizationsBeyondTheMaximumRetentionArePurgedEvenForActivePrincipals() {
        String principal = principal();
        String ancient = insertAuthorization(principal, Duration.ofDays(8));
        String recent = insertAuthorization(principal, Duration.ofMinutes(5));

        cleanupJob.purgeExpired();

        assertThat(remaining(ancient, recent)).containsExactly(recent);
    }

    private static String principal() {
        return "person-" + UUID.randomUUID() + "@example.com";
    }

    private String insertAuthorization(String principal, Duration age) {
        String id = UUID.randomUUID().toString();
        Instant issuedAt = Instant.now().minus(age);
        jdbcTemplate.update("""
                INSERT INTO oauth2_authorization (id, registered_client_id, principal_name, authorization_grant_type,
                    authorization_code_value, authorization_code_issued_at, access_token_value, access_token_issued_at)
                VALUES (?, 'ui', ?, 'authorization_code', ?, ?, ?, ?)
                """, id, principal, "code-" + id, Timestamp.from(issuedAt), "token-" + id, Timestamp.from(issuedAt.plusSeconds(1)));
        return id;
    }

    private List<String> remaining(String... ids) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM oauth2_authorization WHERE id IN (%s)".formatted(String.join(",", Collections.nCopies(ids.length, "?"))),
                String.class, (Object[]) ids);
    }
}

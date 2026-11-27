package org.letspeppol.proxy.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Independent commits keep the submission identity/attempt durable even if the scheduler rolls back. */
public class PhossSubmissionStore {
    public record Submission(UUID documentId, String instanceId, String fingerprint, Instant createdOn,
                             boolean attempted, String failure, String report) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public PhossSubmissionStore(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Submission prepare(UUID id, String fingerprint) {
        return commit(() -> {
            jdbc.update("INSERT INTO proxy.phoss_submission(document_id, instance_id, fingerprint) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                    id, UUID.randomUUID().toString(), fingerprint);
            Submission saved = load(id);
            if (!saved.fingerprint().equals(fingerprint)) {
                throw new IllegalArgumentException("PHOSS submission content changed; use a new document ID");
            }
            return saved;
        });
    }

    public boolean claim(UUID id) {
        return commit(() -> jdbc.update("UPDATE proxy.phoss_submission SET attempted = true WHERE document_id = ? AND attempted = false", id) == 1);
    }

    public Submission load(UUID id) {
        return jdbc.queryForObject("SELECT * FROM proxy.phoss_submission WHERE document_id = ?",
                (rs, row) -> new Submission(rs.getObject("document_id", UUID.class), rs.getString("instance_id"),
                        rs.getString("fingerprint"), rs.getTimestamp("created_on").toInstant(),
                        rs.getBoolean("attempted"), rs.getString("failure"), rs.getString("report")), id);
    }

    public void report(UUID id, String report) {
        commit(() -> jdbc.update("UPDATE proxy.phoss_submission SET report = ? WHERE document_id = ?", report, id));
    }

    public void fail(UUID id, String reason) {
        commit(() -> jdbc.update("UPDATE proxy.phoss_submission SET failure = ? WHERE document_id = ?", reason, id));
    }

    private <T> T commit(Supplier<T> work) {
        return Objects.requireNonNull(transaction.execute(status -> work.get()));
    }
}

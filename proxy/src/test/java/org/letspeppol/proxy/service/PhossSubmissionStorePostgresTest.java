package org.letspeppol.proxy.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;

/** Like ProxyApplicationTests, this uses the configured local PostgreSQL test database. */
@SpringBootTest(properties = {
        "e-invoice.organisation.api-key=", "scrada.company-key=", "scrada.api-key=",
        "recommand.api-key=", "recommand.api-secret=", "phoss.enabled=false"
})
@Import(PhossSubmissionStore.class)
class PhossSubmissionStorePostgresTest {
    @Autowired PhossSubmissionStore store;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void identityClaimAndReportSurviveCallerRollbackAndCannotBeReusedForDifferentContent() {
        UUID id = UUID.randomUUID();
        new TransactionTemplate(transactions).execute(status -> {
            store.prepare(id, "original-content");
            assertThat(store.claim(id)).isTrue();
            store.report(id, "{\"as4MessageId\":\"example@local\"}");
            status.setRollbackOnly();
            return null;
        });
        var saved = store.load(id);
        assertThat(saved.attempted()).isTrue();
        assertThat(saved.report()).contains("example@local");
        assertThat(store.prepare(id, "original-content").instanceId()).isEqualTo(saved.instanceId());
        assertThat(store.claim(id)).isFalse();
        assertThatThrownBy(() -> store.prepare(id, "changed-content"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("use a new document ID");
    }

    @Test
    void concurrentPreparationsReturnOneIdentityAndOnlyOneCallerCanSubmit() {
        UUID id = UUID.randomUUID();
        var first = CompletableFuture.supplyAsync(() -> store.prepare(id, "same-content"));
        var second = CompletableFuture.supplyAsync(() -> store.prepare(id, "same-content"));
        assertThat(first.join().instanceId()).isEqualTo(second.join().instanceId());
        var claim1 = CompletableFuture.supplyAsync(() -> store.claim(id));
        var claim2 = CompletableFuture.supplyAsync(() -> store.claim(id));
        assertThat(java.util.List.of(claim1.join(), claim2.join())).containsExactlyInAnyOrder(true, false);
    }
}

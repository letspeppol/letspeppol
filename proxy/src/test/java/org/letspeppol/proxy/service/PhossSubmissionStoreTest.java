package org.letspeppol.proxy.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PhossSubmissionStoreTest {
    @Test
    @SuppressWarnings("unchecked")
    void identityAndAttemptUseIndependentCommitsAndOnlyOneAttemptCanWin() {
        var jdbc = mock(JdbcTemplate.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        var store = new PhossSubmissionStore(jdbc, manager);
        UUID id = UUID.randomUUID();
        var saved = new PhossSubmissionStore.Submission(id, "persisted-instance", "original-content",
                Instant.now(), false, null, null);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(id))).thenReturn(saved);
        when(jdbc.update(contains("SET attempted = true"), eq(id))).thenReturn(1, 0);
        assertThat(store.prepare(id, "original-content")).isSameAs(saved);
        assertThat(store.claim(id)).isTrue();
        assertThat(store.claim(id)).isFalse();
        var definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(manager, times(3)).getTransaction(definition.capture());
        assertThat(definition.getAllValues()).allSatisfy(d -> assertThat(d.getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW));
        verify(manager, times(3)).commit(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsContentChangesUnderAnExistingIdentity() {
        var jdbc = mock(JdbcTemplate.class);
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        UUID id = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(id))).thenReturn(
                new PhossSubmissionStore.Submission(id, "persisted-instance", "original-content", Instant.now(), true, null, null));
        assertThatThrownBy(() -> new PhossSubmissionStore(jdbc, manager).prepare(id, "different-content"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("use a new document ID");
        verify(manager).rollback(any());
        verify(manager, never()).commit(any());
    }
}

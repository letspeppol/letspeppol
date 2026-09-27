package org.letspeppol.kyc.repository;

import org.letspeppol.kyc.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<Account> findByExternalId(UUID externalId);

    @Transactional
    @Modifying
    @Query(value = """
            update account set totp_last_used_step = :step
            where id = :id and (totp_last_used_step is null or totp_last_used_step < :step)
            """, nativeQuery = true)
    int claimTotpStep(@Param("id") Long id, @Param("step") long step);

    @Transactional
    @Modifying
    @Query(value = """
            update account set totp_recovery_codes = :updated
            where id = :id and totp_recovery_codes = :expected
            """, nativeQuery = true)
    int replaceTotpRecoveryCodes(@Param("id") Long id, @Param("expected") String expected, @Param("updated") String updated);

    @Transactional
    @Modifying
    @Query(value = """
            update account set totp_secret = null, totp_enabled = false, totp_recovery_codes = null, totp_last_used_step = null
            where id = :id
            """, nativeQuery = true)
    void disableTotp(@Param("id") Long id);
}

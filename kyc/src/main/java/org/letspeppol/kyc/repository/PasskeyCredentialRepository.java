package org.letspeppol.kyc.repository;

import org.letspeppol.kyc.model.PasskeyCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PasskeyCredentialRepository extends JpaRepository<PasskeyCredential, Long> {
    @Query("SELECT p FROM PasskeyCredential p JOIN FETCH p.account WHERE p.credentialId = :credentialId")
    Optional<PasskeyCredential> findByCredentialId(byte[] credentialId);
    List<PasskeyCredential> findAllByAccountId(Long accountId);
    List<PasskeyCredential> findAllByAccountExternalId(UUID externalId);
    void deleteByIdAndAccountId(Long id, Long accountId);
}

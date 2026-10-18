package org.letspeppol.proxy.repository;

import org.letspeppol.proxy.model.Registry;
import org.letspeppol.proxy.model.AccessPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface RegistryRepository extends JpaRepository<Registry, String> {

    long countByAccessPointNot(AccessPoint accessPoint);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select registry from Registry registry where registry.peppolId = :peppolId")
    Optional<Registry> findByIdForSettings(@Param("peppolId") String peppolId);
}

package org.letspeppol.proxy.repository;

import org.letspeppol.proxy.model.Registry;
import org.letspeppol.proxy.model.AccessPoint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistryRepository extends JpaRepository<Registry, String> {

    long countByAccessPointNot(AccessPoint accessPoint);
}

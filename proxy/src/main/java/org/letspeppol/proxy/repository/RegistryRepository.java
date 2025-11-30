package org.letspeppol.proxy.repository;

import org.letspeppol.proxy.model.Registry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistryRepository extends JpaRepository<Registry, String> {
}

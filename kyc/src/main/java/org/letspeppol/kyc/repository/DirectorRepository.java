package org.letspeppol.kyc.repository;

import org.letspeppol.kyc.model.kbo.Director;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DirectorRepository extends JpaRepository<Director, Long> {
    @Override
    @EntityGraph(attributePaths = "company")
    Optional<Director> findById(Long id);
}


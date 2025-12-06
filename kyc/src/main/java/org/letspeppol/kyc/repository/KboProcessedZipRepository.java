package org.letspeppol.kyc.repository;

import org.letspeppol.kyc.model.kbo.KboProcessedZip;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KboProcessedZipRepository extends JpaRepository<KboProcessedZip, Long> {

    boolean existsByFilename(String filename);
}


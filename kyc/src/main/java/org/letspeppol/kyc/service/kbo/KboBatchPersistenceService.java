package org.letspeppol.kyc.service.kbo;

import org.letspeppol.kyc.model.kbo.Company;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class KboBatchPersistenceService {

    private final CompanyRepository companyRepository;

    public KboBatchPersistenceService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    /**
     * Persist the given batch of companies in a single transaction.
     */
    @Transactional
    public List<Company> saveBatch(List<Company> batch) {
        // Defensive copy to avoid callers mutating the list while JPA operates on it
        return companyRepository.saveAll(new ArrayList<>(batch));
    }
}


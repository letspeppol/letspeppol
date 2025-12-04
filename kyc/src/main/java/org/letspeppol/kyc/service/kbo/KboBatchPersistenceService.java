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

    @Transactional
    public List<Company> saveBatch(List<Company> batch) {
        return companyRepository.saveAll(new ArrayList<>(batch));
    }

    @Transactional
    public void deleteByPeppolIds(List<String> peppolIds) {
        for (String peppolId : peppolIds) {
            companyRepository.findByPeppolId(peppolId).ifPresent(companyRepository::delete);
        }
    }
}

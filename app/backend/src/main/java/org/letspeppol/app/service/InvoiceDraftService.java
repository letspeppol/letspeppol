package org.letspeppol.app.service;

import org.letspeppol.app.dto.InvoiceDraftDto;
import org.letspeppol.app.exception.NotFoundException;
import org.letspeppol.app.mapper.InvoiceDraftMapper;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.InvoiceDraft;
import org.letspeppol.app.repository.CompanyRepository;
import org.letspeppol.app.repository.InvoiceDraftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class InvoiceDraftService {

    private final CompanyRepository companyRepository;
    private final InvoiceDraftRepository invoiceDraftRepository;

    public List<InvoiceDraftDto> findByCompanyNumber(String companyNumber) {
        return invoiceDraftRepository.findByOwningCompany(companyNumber).stream()
                .map(InvoiceDraftMapper::toDto)
                .toList();
    }

    public InvoiceDraftDto createDraft(String companyNumber, InvoiceDraftDto draftDto) {
        Company company = companyRepository.findByCompanyNumber(companyNumber).orElseThrow(() -> new NotFoundException("Company does not exist"));
        InvoiceDraft draft = new InvoiceDraft(
                draftDto.docType(),
                draftDto.docId(),
                draftDto.counterPartyName(),
                draftDto.createdAt(),
                draftDto.dueDate(),
                draftDto.amount(),
                draftDto.xml()
        );
        draft.setCompany(company);
        invoiceDraftRepository.save(draft);
        return InvoiceDraftMapper.toDto(draft);
    }

    public InvoiceDraftDto updateDraft(String companyNumber, Long id, InvoiceDraftDto draftDto) {
        InvoiceDraft draft = invoiceDraftRepository.findById(id).orElseThrow(() -> new NotFoundException("Draft does not exist"));
        draft.setDocType(draftDto.docType());
        draft.setDocId(draftDto.docId());
        draft.setCounterPartyName(draftDto.counterPartyName());
        draft.setCreatedAt(draftDto.createdAt());
        draft.setDueDate(draftDto.dueDate());
        draft.setAmount(draftDto.amount());
        draft.setXml(draftDto.xml());
        invoiceDraftRepository.save(draft);
        return InvoiceDraftMapper.toDto(draft);
    }

    public void deleteDraft(Long id, String companyNumber) {
        invoiceDraftRepository.deleteByIdAndCompanyNumber(companyNumber, id);
    }

}

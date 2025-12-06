package org.letspeppol.app.service;

import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import org.letspeppol.app.dto.DocumentDto;
import org.letspeppol.app.dto.UblDocumentDto;
import org.letspeppol.app.dto.UblDto;
import org.letspeppol.app.exception.*;
import org.letspeppol.app.exception.SecurityException;
import org.letspeppol.app.mapper.DocumentMapper;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.Document;
import org.letspeppol.app.model.DocumentDirection;
import org.letspeppol.app.repository.CompanyRepository;
import org.letspeppol.app.repository.DocumentRepository;
import org.letspeppol.app.util.UblParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Transactional
@Service
public class DocumentService {

    private final CompanyRepository companyRepository;
    private final DocumentRepository documentRepository;
    private final Counter documentBackupCounter;
    private final Counter documentCreateCounter;
    private final Counter documentSendCounter;
    private final Counter documentPaidCounter;

    @Value("${app.data.dir:#{null}}")
    private String dataDirectory;

    public void backupFile(Document document) throws Exception {
        Path filePath = Paths.get(
                (dataDirectory == null || dataDirectory.isBlank()) ? System.getProperty("java.io.tmpdir") : dataDirectory,
                "backup",
                document.getCompany().getPeppolId(),
                document.getDirection().toString(),
                String.valueOf(document.getProxyOn().atZone(ZoneId.systemDefault()).getYear()),
                String.valueOf(document.getProxyOn().atZone(ZoneId.systemDefault()).getMonth()),
                document.getId() + ".ubl"
        );
        Path parent = filePath.getParent();
        if (parent != null) {
            System.out.println("Writing backup folder to: " + parent);
            Files.createDirectories(parent);
        }
        System.out.println("Writing file as backup to: " + filePath);
        Files.writeString(filePath, document.getUbl(), StandardCharsets.UTF_8);
        documentBackupCounter.increment();
    }

    public List<DocumentDto> findAll(String peppolId) {
        return documentRepository.findAllByOwnerPeppolId(peppolId).stream()
                .map(DocumentMapper::toDto)
                .toList();
    }

    public DocumentDto findById(String peppolId, UUID id) {
//        Company company = companyRepository.findByPeppolId(peppolId).orElseThrow(() -> new NotFoundException("Company does not exist"));
        Document document =  documentRepository.findById(id).orElseThrow(() -> new NotFoundException("Document does not exist"));
        if (!peppolId.equals(document.getOwnerPeppolId())) {
            throw new SecurityException(AppErrorCodes.PEPPOL_ID_MISMATCH);
        }
        return DocumentMapper.toDto(document);
    }

    public DocumentDto createFromUbl(String peppolId, String ublXml, boolean draft, Instant schedule) {
        Company company = companyRepository.findByPeppolId(peppolId).orElseThrow(() -> new NotFoundException("Company does not exist"));
        UblDto ublDto;
        try {
            ublDto = UblParser.parse(ublXml);
        } catch (ParserConfigurationException | IOException | SAXException | XPathExpressionException e) {
            throw new UblException(e.toString());
        }
        if (!peppolId.equals(ublDto.senderPeppolId())) {
            throw new SecurityException(AppErrorCodes.PEPPOL_ID_MISMATCH);
        }
        Document document = new Document(
                null,
                DocumentDirection.OUTGOING,
                peppolId,
                ublDto.receiverPeppolId(),
                null,
                schedule,
                null,
                null,
                ublXml,
                draft ? Instant.now() : null,
                null,
                null,
                ublDto.partnerName(),
                ublDto.invoiceReference(),
                ublDto.buyerReference(),
                ublDto.orderReference(),
                ublDto.type(),
                ublDto.currency(),
                ublDto.amount(),
                ublDto.issueDate(),
                ublDto.dueDate(),
                ublDto.paymentTerms()
        );
        document.setCompany(company);
        if (!draft) {
            //TODO : send document
        }
        documentRepository.save(document);
        //TODO : do we want to backUp drafts and not on proxy documents ?
//        try {
//            backupFile(document);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
        documentCreateCounter.increment();
        return DocumentMapper.toDto(document);
    }

    public DocumentDto create(UblDocumentDto ublDocumentDto) {
        Company company = companyRepository.findByPeppolId(ublDocumentDto.ownerPeppolId()).orElseThrow(() -> new NotFoundException("Company does not exist"));
        UblDto ublDto;
        try {
            ublDto = UblParser.parse(ublDocumentDto.ubl());
        } catch (ParserConfigurationException | IOException | SAXException | XPathExpressionException e) {
            throw new UblException(e.toString());
        }
        Document document = new Document(
                ublDocumentDto.id(),
                ublDocumentDto.direction(),
                ublDocumentDto.ownerPeppolId(),
                ublDocumentDto.partnerPeppolId(),
                ublDocumentDto.createdOn(),
                ublDocumentDto.scheduledOn(),
                ublDocumentDto.processedOn(),
                ublDocumentDto.processedStatus(),
                ublDocumentDto.ubl(),
                null,
                null,
                null,
                ublDto.partnerName(),
                ublDto.invoiceReference(),
                ublDto.buyerReference(),
                ublDto.orderReference(),
                ublDto.type(),
                ublDto.currency(),
                ublDto.amount(),
                ublDto.issueDate(),
                ublDto.dueDate(),
                ublDto.paymentTerms()
        );
        document.setCompany(company);
        documentRepository.save(document);
        try {
            backupFile(document);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        documentCreateCounter.increment();
        return DocumentMapper.toDto(document);
    }

    public DocumentDto update(String peppolId, UUID id, String ublXml, boolean draft, Instant schedule) {
//        Company company = companyRepository.findByPeppolId(peppolId).orElseThrow(() -> new NotFoundException("Company does not exist"));
        Document document = documentRepository.findById(id).orElseThrow(() -> new NotFoundException("Document does not exist"));
        if (!peppolId.equals(document.getOwnerPeppolId())) {
            throw new SecurityException(AppErrorCodes.PEPPOL_ID_MISMATCH);
        }
        if (document.getProcessedOn() != null) {
            throw new ConflictException("Document is already processed"); //TODO : port to 409 Conflict ?
        }
        UblDto ublDto = null;
        try {
            ublDto = UblParser.parse(ublXml);
        } catch (ParserConfigurationException | IOException | SAXException | XPathExpressionException e) {
            throw new UblException(e.toString());
        }
        if (!peppolId.equals(ublDto.senderPeppolId())) {
            throw new SecurityException(AppErrorCodes.PEPPOL_ID_MISMATCH);
        }
        document.setPartnerPeppolId(ublDto.receiverPeppolId());
        document.setScheduledOn(schedule);
        document.setUbl(ublXml);
        if (draft && document.getProxyOn() != null) { //TODO : inform user about not draftable ?
            document.setDraftedOn(Instant.now());
        } else {
            document.setDraftedOn(null);
        }
        document.setPartnerName(ublDto.partnerName());
        document.setInvoiceReference(ublDto.invoiceReference());
        document.setBuyerReference(ublDto.buyerReference());
        document.setOrderReference(ublDto.orderReference());
        document.setType(ublDto.type());
        document.setCurrency(ublDto.currency());
        document.setAmount(ublDto.amount());
        document.setIssueDate(ublDto.issueDate());
        document.setDueDate(ublDto.dueDate());
        document.setPaymentTerms(ublDto.paymentTerms());
        if (document.getProxyOn() != null) {
            //TODO : send update ?
        } else if (!draft) {
            //TODO : send document ?
        }
        documentRepository.save(document);
        //TODO : do we want to backUp drafts and not on proxy documents ?
//        try {
//            backupFile(document);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
        return DocumentMapper.toDto(document);
    }

    public DocumentDto send(String peppolId, UUID id, Instant schedule) {
        Document document = documentRepository.findById(id).orElseThrow(() -> new NotFoundException("Document does not exist"));
        if (!peppolId.equals(document.getOwnerPeppolId())) {
            throw new SecurityException(AppErrorCodes.PEPPOL_ID_MISMATCH);
        }
        if (document.getProcessedOn() != null) {
            throw new ConflictException("Document is already processed"); //TODO : port to 409 Conflict ?
        }
        document.setScheduledOn(schedule);
        document.setDraftedOn(null);
        if (document.getProxyOn() != null) {
            //TODO : send update ?
        } else {
            //TODO : send document ?
        }
        documentRepository.save(document);
        //TODO : do we want to backUp drafts and not on proxy documents ?
//        try {
//            backupFile(document);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
        documentSendCounter.increment();
        return DocumentMapper.toDto(document);
    }

    public DocumentDto read(String peppolId, UUID id) {
        Document document = documentRepository.findById(id).orElseThrow(() -> new NotFoundException("Document does not exist"));
        if (!peppolId.equals(document.getOwnerPeppolId())) {
            throw new SecurityException(AppErrorCodes.PEPPOL_ID_MISMATCH);
        }
        document.setReadOn(Instant.now());
        documentRepository.save(document);
        return DocumentMapper.toDto(document);
    }

    public DocumentDto paid(String peppolId, UUID id) {
        Document document = documentRepository.findById(id).orElseThrow(() -> new NotFoundException("Document does not exist"));
        if (!peppolId.equals(document.getOwnerPeppolId())) {
            throw new SecurityException(AppErrorCodes.PEPPOL_ID_MISMATCH);
        }
        document.setPaidOn(Instant.now());
        documentRepository.save(document);
        documentPaidCounter.increment();
        return DocumentMapper.toDto(document);
    }

    public void delete(String peppolId, UUID id) { //TODO : do we need to send boundaries ?
        documentRepository.deleteByIdAndOwnerPeppolId(id, peppolId);
    }

}

package org.letspeppol.app.service;

import lombok.RequiredArgsConstructor;
import org.letspeppol.app.dto.DocumentDto;
import org.letspeppol.app.exception.NotFoundException;
import org.letspeppol.app.mapper.DocumentMapper;
import org.letspeppol.app.model.Document;
import org.letspeppol.app.model.DocumentDirection;
import org.letspeppol.app.model.DocumentType;
import org.letspeppol.app.repository.DocumentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Transactional
@Service
public class DocumentService {

    @Autowired
    private final DocumentRepository documentRepository;

    public void backupFile(Document document) throws Exception {
        Path filePath = Paths.get(
                //System.getProperty("java.io.tmpdir"),
                "backup",
                document.getCompany().getCompanyNumber(),
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
    }

    public List<DocumentDto> findAll() {
        return documentRepository.findAll().stream()
                .map(DocumentMapper::toDto)
                .toList();
    }

    public DocumentDto findById(UUID id) {
        return DocumentMapper.toDto(documentRepository.findById(id).orElseThrow(() -> new NotFoundException("Document does not exist")));
    }

//    public DocumentDto createFromUbl(String ublXml) {
//        Document document = new Document(
//                UUID.randomUUID(), //TODO : or null ?
//                DocumentDirection.OUTGOING,
//                documentDto.ownerPeppolId(),
//                documentDto.partnerPeppolId(),
//                documentDto.proxyOn(),
//                documentDto.scheduledOn(),
//                documentDto.processedOn(),
//                documentDto.processedStatus(),
//                ublXml,
//                documentDto.draftedOn(),
//                documentDto.readOn(),
//                documentDto.paidOn(),
//                documentDto.partnerName(),
//                documentDto.invoiceReference(),
//                null,
//                null,
//                documentDto.type(),
//                documentDto.currency(),
//                documentDto.amount(),
//                documentDto.issueDate(),
//                documentDto.dueDate(),
//                documentDto.paymentTerms()
//        );
////        document.setCompany();
//        documentRepository.save(document);
//        try {
//            backupFile(document);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//        return DocumentMapper.toDto(document);
//    }

    public DocumentDto create(DocumentDto documentDto) {
        Document document = new Document(
                documentDto.id(),
                documentDto.direction(),
                documentDto.ownerPeppolId(),
                documentDto.partnerPeppolId(),
                documentDto.proxyOn(),
                documentDto.scheduledOn(),
                documentDto.processedOn(),
                documentDto.processedStatus(),
                documentDto.ubl(),
                documentDto.draftedOn(),
                documentDto.readOn(),
                documentDto.paidOn(),
                documentDto.partnerName(),
                documentDto.invoiceReference(),
                null,
                null,
                documentDto.type(),
                documentDto.currency(),
                documentDto.amount(),
                documentDto.issueDate(),
                documentDto.dueDate(),
                documentDto.paymentTerms()
        );
//        document.setCompany();
        documentRepository.save(document);
        try {
            backupFile(document);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return DocumentMapper.toDto(document);
    }

    public DocumentDto update(UUID id, DocumentDto documentDto) {
        Document document = documentRepository.findById(id).orElseThrow(() -> new NotFoundException("Document does not exist"));
//        document.setUserId(documentDto.userId());
//        document.setPlatformId(documentDto.platformId());
//        document.setCreatedOn(documentDto.createdOn());
//        document.setType(documentDto.type());
//        document.setDirection(documentDto.direction());
//        document.setCounterPartyId(documentDto.counterPartyId());
//        document.setCounterPartyName(documentDto.counterPartyName());
//        document.setDocId(documentDto.docId());
//        document.setAmount(documentDto.amount());
//        document.setDueDate(documentDto.dueDate());
//        document.setPaymentTerms(documentDto.paymentTerms());
//        document.setPaid(documentDto.paid());
//        document.setUbl(documentDto.ubl());
//        document.setStatus(documentDto.status());
//        documentRepository.save(document);
        return DocumentMapper.toDto(document);
    }

    public void delete(UUID id) {
        documentRepository.deleteById(id);
    }

}

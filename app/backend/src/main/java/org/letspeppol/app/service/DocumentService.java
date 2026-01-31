package org.letspeppol.app.service;

import io.micrometer.core.instrument.Counter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.app.dto.*;
import org.letspeppol.app.exception.*;
import org.letspeppol.app.exception.SecurityException;
import org.letspeppol.app.mapper.DocumentMapper;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.Document;
import org.letspeppol.app.model.DocumentDirection;
import org.letspeppol.app.repository.CompanyRepository;
import org.letspeppol.app.repository.DocumentRepository;
import org.letspeppol.app.repository.DocumentSpecifications;
import org.letspeppol.app.util.UblParser;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.xml.sax.SAXException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class DocumentService {

    static final ZoneId ZONE = ZoneId.of("Europe/Brussels");

    private final CompanyRepository companyRepository;
    private final DocumentRepository documentRepository;
    private final BackupService backupService;
    private final ValidationService validationService;
    private final NotificationService notificationService;
    private final JwtService jwtService;
    @Qualifier("proxyWebClient")
    private final WebClient proxyWebClient;
    private final Counter documentBackupCounter;
    private final Counter documentCreateCounter;
    private final Counter documentSendCounter;
    private final Counter documentPaidCounter;

    @Value("${proxy.synchronize.delay-ms:60000}")
    private long synchronizeDelay;

    private UblDto readUBL(DocumentDirection documentDirection, String ublXml, String peppolId, boolean draft) {
        if (ublXml == null || ublXml.isBlank()) {
            throw new RuntimeException("Missing UBL content"); //TODO : use proper exceptions
        }
        if (!validationService.validateUblXml(ublXml).isValid() && !draft) {
            throw new RuntimeException("Invalid UBL content"); //TODO : use proper exceptions
        }
        try {
            UblDto ublDto = UblParser.parse(documentDirection, ublXml);
            if (documentDirection == DocumentDirection.OUTGOING && !peppolId.equals(ublDto.senderPeppolId())) {
                throw new SecurityException(AppErrorCodes.PEPPOL_ID_MISMATCH);
            }
            if (documentDirection == DocumentDirection.INCOMING && !peppolId.equals(ublDto.receiverPeppolId())) {
                if (peppolId.equals(ublDto.receiverPeppolId().replace("9925:BE", "0208:"))) { //TODO : fix this madness, why is Peppol allowing documents to PeppolID that was not registered ?
                    log.warn("The user {} received a Peppol document for {} but will be allowed", peppolId, ublDto.receiverPeppolId());
                    return ublDto;
                }
                throw new SecurityException(AppErrorCodes.PEPPOL_ID_MISMATCH);
            }
            return ublDto;
        } catch (ParserConfigurationException | IOException | SAXException | XPathExpressionException e) {
            throw new UblException(e.toString());
        }
    }

    public List<DocumentDto> findAll(String peppolId) {
        return documentRepository.findAllByOwnerPeppolId(peppolId).stream()
                .map(DocumentMapper::toDto)
                .toList();
    }

    public Page<DocumentDto> findAll(DocumentFilter filter, Pageable pageable) {
        Pageable effectivePageable = pageable;
        Sort sort = Sort.by(Sort.Direction.DESC, "issueDate").and(Sort.by(Sort.Direction.DESC, "createdOn"));
        if (effectivePageable == null) {
            effectivePageable = PageRequest.of(0, 20, sort);
        } else if (effectivePageable.getSort().isUnsorted()) {
            effectivePageable = PageRequest.of(
                    effectivePageable.getPageNumber(),
                    effectivePageable.getPageSize(),
                    sort
            );
        }

        Page<Document> page = documentRepository.findAll(DocumentSpecifications.build(filter), effectivePageable);
        return page.map(DocumentMapper::toDto);
    }

    public DocumentDto findById(String peppolId, UUID id) {
//        Company company = companyRepository.findByPeppolId(peppolId).orElseThrow(() -> new NotFoundException("Company does not exist"));
        Document document =  documentRepository.findById(id).orElseThrow(() -> new NotFoundException("Document does not exist"));
        if (!peppolId.equals(document.getOwnerPeppolId())) {
            throw new SecurityException(AppErrorCodes.PEPPOL_ID_MISMATCH);
        }
        return DocumentMapper.toDto(document);
    }

    public void synchronize(String peppolId, String tokenValue) throws InterruptedException {
        companyRepository.findByPeppolId(peppolId).ifPresent(company -> {
            synchronizeNewDocuments(tokenValue);
            synchronizeDocuments(peppolId, tokenValue);
        });
    }

    public DocumentDto createFromUbl(String peppolId, String ublXml, boolean draft, Instant schedule, String tokenValue) {
        Company company = companyRepository.findByPeppolId(peppolId).orElseThrow(() -> new NotFoundException("Company does not exist"));
        UblDto ublDto = readUBL(DocumentDirection.OUTGOING, ublXml, peppolId, draft);
        if (!draft) {
             if (documentRepository.existsByInvoiceReferenceAndOwnerPeppolId(ublDto.invoiceReference(), peppolId)) {
                 throw new AppException(AppErrorCodes.INVOICE_NUMBER_ALREADY_USED);
             }
        }
        Document document = new Document(
                null, //Hibernate generates UUID
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
        document = documentRepository.save(document);
        documentCreateCounter.increment();
        if (!draft) {
            backupService.backupFile(document); //TODO : do we want to backUp drafts and not on proxy documents ?
            documentBackupCounter.increment();
            document = deliver(document, tokenValue);
            documentSendCounter.increment();
        }
        return DocumentMapper.toDto(document);
    }

    public void create(UblDocumentDto ublDocumentDto) {
        Company company = companyRepository.findByPeppolId(ublDocumentDto.ownerPeppolId()).orElseThrow(() -> new NotFoundException("Company does not exist"));
        UblDto ublDto = readUBL(ublDocumentDto.direction(), ublDocumentDto.ubl(), ublDocumentDto.ownerPeppolId(), false);
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
        document = documentRepository.save(document);
        documentCreateCounter.increment();
        backupService.backupFile(document);
        documentBackupCounter.increment();

        if (DocumentDirection.INCOMING.equals(document.getDirection()) && company.isEnableEmailNotification()) {
            notificationService.notifyIncomingDocument(company, document);
        }

//        return DocumentMapper.toDto(document); //TODO : do we need to return something or are we only going to use this for received documents ?
    }

    public DocumentDto update(String peppolId, UUID id, String ublXml, boolean draft, Instant schedule, String tokenValue) {
//        Company company = companyRepository.findByPeppolId(peppolId).orElseThrow(() -> new NotFoundException("Company does not exist"));
        Document document = documentRepository.findById(id).orElseThrow(() -> new NotFoundException("Document does not exist"));
        if (!peppolId.equals(document.getOwnerPeppolId())) {
            throw new SecurityException(AppErrorCodes.PEPPOL_ID_MISMATCH);
        }
        if (document.getProcessedOn() != null) {
            throw new ConflictException("Document is already processed"); //TODO : port to 409 Conflict ?
        }
        UblDto ublDto = readUBL(DocumentDirection.OUTGOING, ublXml, peppolId, draft);
        document.setPartnerPeppolId(ublDto.receiverPeppolId());
        document.setScheduledOn(schedule);
        document.setUbl(ublXml);
        if (draft && document.getProxyOn() == null) { //TODO : inform user about not draftable ?
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
        if (document.getProxyOn() == null) {
            document = documentRepository.save(document); //Only save in database when it is not on proxy yet, else the safe is only allowed when it is proper delivered as proxy has the truth
        }
        if (!draft) {
            backupService.backupFile(document); //TODO : do we want to backUp drafts and not on proxy documents ?
            documentBackupCounter.increment();
            document = deliver(document, tokenValue);
            documentSendCounter.increment();
        }
        return DocumentMapper.toDto(document);
    }

    public void updateStatus(UblDocumentDto ublDocumentDto) {
        Document document = documentRepository.findById(ublDocumentDto.id()).orElseThrow(() -> new NotFoundException("Document does not exist"));
        document.setProxyOn(ublDocumentDto.createdOn());
        document.setScheduledOn(ublDocumentDto.scheduledOn());
        document.setProcessedOn(ublDocumentDto.processedOn());
        document.setProcessedStatus(ublDocumentDto.processedStatus());
        documentRepository.save(document);
    }

    public DocumentDto send(String peppolId, UUID id, Instant schedule, String tokenValue) {
        Document document = documentRepository.findById(id).orElseThrow(() -> new NotFoundException("Document does not exist"));
        if (!peppolId.equals(document.getOwnerPeppolId())) {
            throw new SecurityException(AppErrorCodes.PEPPOL_ID_MISMATCH);
        }
        if (document.getProcessedOn() != null) {
            throw new ConflictException("Document is already processed"); //TODO : port to 409 Conflict ?
        }
        document.setScheduledOn(schedule);
        document.setDraftedOn(null);
//        document = documentRepository.save(document); //Not saving, as we only save the proxy returned result
        backupService.backupFile(document);
        documentBackupCounter.increment(); //TODO : how to correctly use these counters ?
        document = deliverOnSchedule(document, tokenValue);
        documentSendCounter.increment();
        return DocumentMapper.toDto(document);
    }

    public DocumentDto read(String peppolId, UUID id) {
        Document document = documentRepository.findById(id).orElseThrow(() -> new NotFoundException("Document does not exist"));
        if (!peppolId.equals(document.getOwnerPeppolId())) {
            throw new SecurityException(AppErrorCodes.PEPPOL_ID_MISMATCH);
        }
        document.setReadOn(Instant.now());
        document = documentRepository.save(document);
        return DocumentMapper.toDto(document);
    }

    public DocumentDto paid(String peppolId, UUID id) {
        Document document = documentRepository.findById(id).orElseThrow(() -> new NotFoundException("Document does not exist"));
        if (!peppolId.equals(document.getOwnerPeppolId())) {
            throw new SecurityException(AppErrorCodes.PEPPOL_ID_MISMATCH);
        }
        if (document.getPaidOn() != null) {
            document.setPaidOn(null);
        } else {
            document.setPaidOn(Instant.now());
        }
        document = documentRepository.save(document);
        documentPaidCounter.increment();
        return DocumentMapper.toDto(document);
    }

    public void delete(String peppolId, UUID id) { //TODO : do we need to send boundaries ?
        documentRepository.deleteByIdAndOwnerPeppolId(id, peppolId);
    }

    private Document deliver(Document document, String tokenValue) { //TODO : use boolean noArchive from Company
        UblDocumentDto ublDocumentDto = ((document.getProxyOn() == null) ? proxyWebClient.post().uri("/sapi/document") : proxyWebClient.put().uri("/sapi/document/"+document.getId()))
                .headers(headers -> headers.setBearerAuth(tokenValue))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UblDocumentDto(
                        document.getId(),
                        document.getDirection(), //DocumentDirection.OUTGOING
                        document.getType(),
                        document.getOwnerPeppolId(),
                        document.getPartnerPeppolId(),
                        document.getCreatedOn(), //null
                        document.getScheduledOn(),
                        document.getProcessedOn(), //null
                        document.getProcessedStatus(), //null
                        document.getUbl()
                ))
                .retrieve()
                .bodyToMono(UblDocumentDto.class)
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException("Could not deliver at PROXY")); //TODO : make correct error

//        document.setId(ublDocumentDto.id()); //Should not be used here, is for other clients that do not generate their own UUID
        document.setDirection(ublDocumentDto.direction());
        document.setOwnerPeppolId(ublDocumentDto.ownerPeppolId());
        document.setPartnerPeppolId(ublDocumentDto.partnerPeppolId());
        document.setProxyOn(ublDocumentDto.createdOn());
        document.setScheduledOn(ublDocumentDto.scheduledOn());
        document.setProcessedOn(ublDocumentDto.processedOn());
        document.setProcessedStatus(ublDocumentDto.processedStatus());
        document.setUbl(ublDocumentDto.ubl());
        document.getCompany().setLastInvoiceReference(document.getInvoiceReference());
        return documentRepository.save(document);
    }

    private Document deliverOnSchedule(Document document, String tokenValue) { //TODO : use boolean noArchive from Company
        UblDocumentDto ublDocumentDto = proxyWebClient.put()
                .uri("/sapi/document/" + document.getId() + "/send")
                .headers(headers -> headers.setBearerAuth(tokenValue))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new UblDocumentDto(
                        document.getId(),
                        document.getDirection(), //DocumentDirection.OUTGOING
                        document.getType(),
                        document.getOwnerPeppolId(),
                        document.getPartnerPeppolId(),
                        document.getCreatedOn(), //null
                        document.getScheduledOn(),
                        document.getProcessedOn(), //null
                        document.getProcessedStatus(), //null
                        document.getUbl()
                ))
                .retrieve()
                .bodyToMono(UblDocumentDto.class)
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException("Could not deliver at PROXY")); //TODO : make correct error

        document.setProxyOn(ublDocumentDto.createdOn());
        document.setScheduledOn(ublDocumentDto.scheduledOn());
        document.setProcessedOn(ublDocumentDto.processedOn());
        document.setProcessedStatus(ublDocumentDto.processedStatus());
        document.getCompany().setLastInvoiceReference(document.getInvoiceReference());
        return documentRepository.save(document);
    }

    @PostConstruct
    public void periodicSynchronize() {
        try {
            String appTokenFromKyc = jwtService.getAppTokenFromKyc();
            List<UblDocumentDto> ublDocumentDtos;
            do {
                ublDocumentDtos = synchronizeNewDocuments(appTokenFromKyc);
            } while (ublDocumentDtos.size() >= 100);
        } catch (Exception e) {
            log.error("Error synchronizing documents", e);
        }
    }

    private List<UblDocumentDto> synchronizeNewDocuments(String tokenValue) {
        //TODO : record Page<T>(List<T> results, Integer total, Integer page, Integer size) {}
        //and use :
        //.bodyToMono(new ParameterizedTypeReference<Page<UblDocumentDto>>() {})
        //.map(Page::results)

        List<UblDocumentDto> ublDocumentDtos = proxyWebClient.get()
                .uri("/sapi/document")
                .headers(headers -> headers.setBearerAuth(tokenValue))
                .retrieve()
                .bodyToFlux(UblDocumentDto.class)
                .collectList()
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException("Could not synchronize with PROXY")); //TODO : make correct error

        List<UUID> ids = new ArrayList<>();
        for (UblDocumentDto ublDocumentDto : ublDocumentDtos) {
            try {
                create(ublDocumentDto);
                ids.add(ublDocumentDto.id());
            } catch (Exception e) {
                log.error("Could not save received document to database", e);
            }
        }

        proxyWebClient.put()
                .uri("/sapi/document/downloaded")
                .headers(headers -> headers.setBearerAuth(tokenValue))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ids)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new IllegalStateException("PROXY " + resp.statusCode() + " :: " + body))
                )
                .toBodilessEntity()
                .block();
        return ublDocumentDtos;
    }

    private void synchronizeDocuments(String peppolId, String tokenValue) {
        List<UUID> ids = documentRepository.findIdsWithPossibleStatusUpdatesOnProxy(peppolId, LocalDate.now(ZONE).plusDays(1).atStartOfDay(ZONE).toInstant());
        List<UblDocumentDto> ublDocumentDtos = proxyWebClient.post()
                .uri("/sapi/document/status")
                .headers(headers -> headers.setBearerAuth(tokenValue))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(ids)
                .retrieve()
                .bodyToFlux(UblDocumentDto.class)
                .collectList()
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException("Could not synchronize with PROXY")); //TODO : make correct error

        for (UblDocumentDto ublDocumentDto : ublDocumentDtos) { //TODO : update to StatusDto !
            updateStatus(ublDocumentDto); //TODO : Could be new NotFoundException, does that make sense ?
        }
    }

    //TODO : combine synchronizeDocuments & synchronizeNewDocuments & add something to send documents that are not draftedOn but also not proxyOn and thus not taken by proxy (due to errors?)
}

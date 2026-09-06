package org.letspeppol.proxy.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.proxy.dto.RegistrationRequest;
import org.letspeppol.proxy.dto.StatusReport;
import org.letspeppol.proxy.dto.recommand.CreateCompanyRequest;
import org.letspeppol.proxy.dto.recommand.CreateCompanyResponse;
import org.letspeppol.proxy.dto.recommand.DocumentResponse;
import org.letspeppol.proxy.dto.recommand.InboxResponse;
import org.letspeppol.proxy.dto.recommand.MarkAsReadRequest;
import org.letspeppol.proxy.dto.recommand.RecommandVariables;
import org.letspeppol.proxy.dto.recommand.SendDocumentRequest;
import org.letspeppol.proxy.dto.recommand.SendDocumentResponse;
import org.letspeppol.proxy.exception.NotFoundException;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.DocumentType;
import org.letspeppol.proxy.model.Registry;
import org.letspeppol.proxy.model.UblDocument;
import org.letspeppol.proxy.repository.RegistryRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class RecommandService implements AccessPointServiceInterface {

    private static final Pattern PEPPOL_ID_PATTERN =
            Pattern.compile("([0-9A-Za-z]{1,16}):([0-9A-Za-z._\\-]{1,128})");

    private final UblDocumentReceiverService ublDocumentReceiverService;
    private final RegistryRepository registryRepository;
    @Qualifier("recommandWebClient")
    private final WebClient recommandWebClient;
    private final ObjectMapper objectMapper;
    private final Counter registerCounter;
    private final Counter unregisterCounter;
    private final Counter documentSendCounter;

    @Value("${recommand.receive-enabled:false}")
    private boolean receiveEnabled;

    @Override
    public AccessPoint getType() {
        return AccessPoint.RECOMMAND;
    }

    /// Recommand API: POST /api/v1/companies
    @Override
    public Map<String, Object> register(String peppolId, RegistrationRequest data) {
        PeppolIdentifier identifier = parsePeppolId(peppolId);
        Objects.requireNonNull(data, "Registration data must not be null");
        try {
            CreateCompanyRequest request = new CreateCompanyRequest(
                    requireText(data.name(), "Company name"),
                    emptyIfNull(data.address()),
                    emptyIfNull(data.postalCode()),
                    emptyIfNull(data.city()),
                    requireText(data.country(), "Company country").toUpperCase(Locale.ROOT),
                    identifier.scheme(),
                    identifier.value(),
                    data.vatNumber(),
                    true,
                    false
            );
            CreateCompanyResponse response = recommandWebClient
                    .post()
                    .uri("/companies")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(CreateCompanyResponse.class)
                    .blockOptional()
                    .orElseThrow(() -> new IllegalStateException("Empty response from Recommand create company"));

            if (!response.success() || response.company() == null || response.company().id() == null) {
                throw new IllegalStateException("Recommand did not return a company ID");
            }
            registerCounter.increment();
            return objectMapper.convertValue(
                    new RecommandVariables(response.company().id(), response.verificationUrl()),
                    new TypeReference<>() {}
            );
        } catch (WebClientResponseException e) {
            throw apiFailure("create company", e);
        } catch (Exception e) {
            throw callFailure("create company", e);
        }
    }

    /// Recommand API: DELETE /api/v1/companies/{companyId}
    @Override
    public void unregister(String peppolId, Map<String, Object> variables) {
        RecommandVariables recommandVariables = parseVariables(variables);
        try {
            recommandWebClient
                    .delete()
                    .uri("/companies/{companyId}", recommandVariables.companyId())
                    .retrieve()
                    .toBodilessEntity()
                    .block();
            unregisterCounter.increment();
        } catch (WebClientResponseException e) {
            throw apiFailure("delete company", e);
        } catch (Exception e) {
            throw callFailure("delete company", e);
        }
    }

    /// Recommand API: POST /api/v1/{companyId}/send
    @Override
    public String sendDocument(UblDocument ublDocument) {
        RecommandVariables variables = variablesFor(ublDocument.getOwnerPeppolId());
        parsePeppolId(ublDocument.getPartnerPeppolId());
        try {
            SendDocumentResponse response = recommandWebClient
                    .post()
                    .uri("/{companyId}/send", variables.companyId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new SendDocumentRequest(
                            ublDocument.getPartnerPeppolId(),
                            "xml",
                            requireText(ublDocument.getUbl(), "UBL document")
                    ))
                    .retrieve()
                    .bodyToMono(SendDocumentResponse.class)
                    .blockOptional()
                    .orElseThrow(() -> new IllegalStateException("Empty response from Recommand send document"));

            if (!response.success() || !response.sentOverPeppol() || response.id() == null) {
                throw new IllegalStateException("Recommand did not send the document over Peppol");
            }
            documentSendCounter.increment();
            return response.id();
        } catch (WebClientResponseException e) {
            throw apiFailure("send document", e);
        } catch (Exception e) {
            throw callFailure("send document", e);
        }
    }

    /// Recommand API: GET /api/v1/documents/{documentId}
    @Override
    public StatusReport getStatus(UblDocument ublDocument) {
        try {
            JsonNode document = getDocument(ublDocument.getAccessPointId());
            ublDocument.setAccessPointDetails(toDetails(document));
            if (document.path("sentOverPeppol").asBoolean(false)) {
                return new StatusReport(true, null);
            }
            return new StatusReport(false, "Recommand did not send the document over Peppol");
        } catch (WebClientResponseException e) {
            throw apiFailure("get document status", e);
        } catch (Exception e) {
            throw callFailure("get document status", e);
        }
    }

    @Override
    public Map<String, Object> getDeliveryDetails(UblDocument ublDocument) {
        try {
            Map<String, Object> details = toDetails(getDocument(ublDocument.getAccessPointId()));
            ublDocument.setAccessPointDetails(details);
            return details;
        } catch (WebClientResponseException e) {
            throw apiFailure("get document details", e);
        } catch (Exception e) {
            throw callFailure("get document details", e);
        }
    }

    private JsonNode getDocument(String documentId) {
        DocumentResponse response = recommandWebClient
                .get()
                .uri("/documents/{documentId}", requireText(documentId, "Recommand document ID"))
                .retrieve()
                .bodyToMono(DocumentResponse.class)
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException("Empty response from Recommand get document"));
        if (!response.success() || response.document() == null) {
            throw new IllegalStateException("Recommand did not return the document");
        }
        return response.document();
    }

    private Map<String, Object> toDetails(JsonNode document) {
        JsonNode detailsNode = document.deepCopy();
        if (detailsNode instanceof ObjectNode objectNode) {
            objectNode.remove(List.of("xml", "parsed"));
        }
        return objectMapper.convertValue(detailsNode, new TypeReference<>() {});
    }

    @Override
    public void updateStatus(String id, String status) {
        log.debug("Ignoring Recommand webhook status for document {} because delivery is synchronized via the documents API", id);
    }

    @Override
    public void receiveDocument(UblDocument ublDocument) {
        ublDocumentReceiverService.createAsReceived(
                Objects.requireNonNull(ublDocument.getType(), "Document type must not be null"),
                requireText(ublDocument.getPartnerPeppolId(), "Sender Peppol ID"),
                requireText(ublDocument.getOwnerPeppolId(), "Receiver Peppol ID"),
                requireText(ublDocument.getUbl(), "UBL document"),
                AccessPoint.RECOMMAND,
                requireText(ublDocument.getAccessPointId(), "Recommand document ID"),
                null
        );
    }

    /// Recommand API: GET /api/v1/inbox, GET /api/v1/documents/{documentId},
    /// and POST /api/v1/documents/{documentId}/mark-as-read
    @Override
    public void receiveDocuments() {
        if (!receiveEnabled) {
            return;
        }
        try {
            InboxResponse inbox = recommandWebClient
                    .get()
                    .uri(uriBuilder -> uriBuilder.path("/inbox").build())
                    .retrieve()
                    .bodyToMono(InboxResponse.class)
                    .blockOptional()
                    .orElseThrow(() -> new IllegalStateException("Empty response from Recommand inbox"));
            if (!inbox.success() || inbox.documents() == null) {
                throw new IllegalStateException("Recommand did not return inbox documents");
            }

            inbox.documents().stream()
                    .filter(document -> "incoming".equals(document.direction()))
                    .filter(document -> document.readAt() == null)
                    .forEach(this::receiveInboxDocument);
        } catch (WebClientResponseException e) {
            throw apiFailure("get inbox", e);
        } catch (Exception e) {
            throw callFailure("get inbox", e);
        }
    }

    private void receiveInboxDocument(InboxResponse.InboxDocument inboxDocument) {
        JsonNode document = getDocument(inboxDocument.id());
        String ubl = textValue(document, "xml");
        DocumentType documentType = toDocumentType(textValue(document, "type"), textValue(document, "docTypeId"));
        String senderId = textValue(document, "senderId");
        String receiverId = textValue(document, "receiverId");

        log.debug("Receiving Recommand document {} from {} to {}", inboxDocument.id(), senderId, receiverId);
        ublDocumentReceiverService.createAsReceived(
                documentType,
                senderId,
                receiverId,
                ubl,
                AccessPoint.RECOMMAND,
                inboxDocument.id(),
                () -> markAsRead(inboxDocument.id())
        );
    }

    private void markAsRead(String documentId) {
        recommandWebClient
                .post()
                .uri("/documents/{documentId}/mark-as-read", documentId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new MarkAsReadRequest(true))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    private DocumentType toDocumentType(String type, String docTypeId) {
        return switch (type) {
            case "invoice" -> DocumentType.INVOICE;
            case "creditNote" -> DocumentType.CREDIT_NOTE;
            default -> {
                if (docTypeId.contains("CreditNote-2")) {
                    yield DocumentType.CREDIT_NOTE;
                }
                if (docTypeId.contains("Invoice-2")) {
                    yield DocumentType.INVOICE;
                }
                throw new IllegalArgumentException("Unsupported Recommand document type: " + type);
            }
        };
    }

    private String textValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalStateException("Recommand document has no " + field);
        }
        return value.textValue();
    }

    private RecommandVariables variablesFor(String peppolId) {
        Registry registry = registryRepository.findById(peppolId)
                .orElseThrow(() -> new NotFoundException("PeppolId " + peppolId + " is not registered here"));
        if (registry.getAccessPoint() != AccessPoint.RECOMMAND) {
            throw new IllegalStateException("PeppolId " + peppolId + " is not registered with Recommand");
        }
        return parseVariables(registry.getVariables());
    }

    private RecommandVariables parseVariables(Map<String, Object> variables) {
        if (variables == null) {
            throw new IllegalStateException("Missing Recommand registration variables");
        }
        RecommandVariables result = objectMapper.convertValue(variables, RecommandVariables.class);
        requireText(result.companyId(), "Recommand company ID");
        return result;
    }

    private PeppolIdentifier parsePeppolId(String peppolId) {
        Matcher matcher = PEPPOL_ID_PATTERN.matcher(requireText(peppolId, "Peppol ID").trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid Peppol ID format");
        }
        return new PeppolIdentifier(matcher.group(1), matcher.group(2));
    }

    private String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }

    private String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    private RuntimeException apiFailure(String operation, WebClientResponseException e) {
        log.error("Recommand {} API error {} {}: {}", operation, e.getStatusCode(), e.getStatusText(), e.getResponseBodyAsString(), e);
        return new RuntimeException("Recommand API error while trying to " + operation + ": " + e.getStatusCode(), e);
    }

    private RuntimeException callFailure(String operation, Exception e) {
        log.error("Recommand {} API call failed", operation, e);
        return new RuntimeException("Failed to call Recommand API to " + operation, e);
    }

    private record PeppolIdentifier(String scheme, String value) {}
}

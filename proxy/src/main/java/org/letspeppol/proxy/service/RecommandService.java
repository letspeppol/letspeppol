package org.letspeppol.proxy.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.proxy.dto.RegistrationRequest;
import org.letspeppol.proxy.dto.PeppolSettings;
import org.letspeppol.proxy.exception.AlreadyRegisteredException;
import org.letspeppol.proxy.dto.StatusReport;
import org.letspeppol.proxy.dto.recommand.CreateCompanyRequest;
import org.letspeppol.proxy.dto.recommand.CreateCompanyResponse;
import org.letspeppol.proxy.dto.recommand.DocumentResponse;
import org.letspeppol.proxy.dto.recommand.InboxResponse;
import org.letspeppol.proxy.dto.recommand.MarkAsReadRequest;
import org.letspeppol.proxy.dto.recommand.RecommandVariables;
import org.letspeppol.proxy.dto.recommand.SendDocumentRequest;
import org.letspeppol.proxy.dto.recommand.SendDocumentResponse;
import org.letspeppol.proxy.dto.recommand.VerifyCompanyResponse;
import org.letspeppol.proxy.exception.NotFoundException;
import org.letspeppol.proxy.exception.SendAuthorizationException;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.DocumentType;
import org.letspeppol.proxy.model.Registry;
import org.letspeppol.proxy.model.UblDocument;
import org.letspeppol.proxy.repository.RegistryRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
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

    static final String BILLING_PROCESS = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";
    static final String INVOICE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2::Invoice##urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0::2.1";
    static final String CREDIT_NOTE = "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2::CreditNote##urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0::2.1";
    static final String SELF_BILLING_PROCESS = "urn:fdc:peppol.eu:2017:poacc:selfbilling:01:1.0";
    static final String SELF_BILLING_INVOICE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2::Invoice##urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:selfbilling:3.0::2.1";
    static final String SELF_BILLING_CREDIT_NOTE = "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2::CreditNote##urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:selfbilling:3.0::2.1";

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

    @Override
    public AccessPoint getType() {
        return AccessPoint.RECOMMAND;
    }

    @Override
    public void validateRegistration(String peppolId, RegistrationRequest data) {
        parsePeppolId(peppolId);
        if (!peppolId.startsWith("0208:")) {
            throw new IllegalArgumentException("Recommand registration requires the company's 0208 enterprise identifier");
        }
        Objects.requireNonNull(data, "Registration data must not be null");
        if (data.signedContract() == null || data.signedContract().length == 0) {
            throw new IllegalArgumentException("A signed PDF contract is required for Recommand registration");
        }
        requireText(data.name(), "Company name");
        requireText(data.country(), "Company country");
    }

    /// Recommand API: POST /api/v1/companies
    @Override
    public void register(Registry registry, RegistrationRequest data) {
        String peppolId = registry.getPeppolId();
        validateRegistration(peppolId, data);
        PeppolIdentifier identifier = parsePeppolId(peppolId);
        byte[] signedContract = data.signedContract();
        String operation = "create company";
        String companyId = null;
        try {
            CreateCompanyRequest request = new CreateCompanyRequest(
                    data.name(),
                    emptyIfNull(data.address()),
                    emptyIfNull(data.postalCode()),
                    emptyIfNull(data.city()),
                    data.country().toUpperCase(Locale.ROOT),
                    identifier.scheme(),
                    identifier.value(),
                    data.vatNumber(),
                    false,
                    true
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

            if (!response.success() || response.company() == null || response.company().id() == null
                    || response.company().id().isBlank()) {
                throw new IllegalStateException("Recommand did not return a company ID");
            }
            companyId = response.company().id();
            operation = "set up company receiving";
            Map<String, Object> variables = objectMapper.convertValue(
                    new RecommandVariables(companyId, response.verificationUrl()),
                    new TypeReference<>() {}
            );
            createIdentifier(companyId, identifier);
            createDocumentTypes(companyId);
            operation = "verify company by contract";
            verifyCompanyByContract(companyId, signedContract);
            boolean receiving = false;
            operation = "activate receiving";
            if (canReceiveByDefault(peppolId)) {
                try {
                    updateReceiving(companyId, true, registry);
                    receiving = true;
                } catch (AlreadyRegisteredException conflict) {
                    log.info("Registered {} for sending only after a recipient registration conflict", peppolId);
                }
            }
            registry.setVariables(variables);
            registry.setReceiveDocuments(receiving);
            registerCounter.increment();
        } catch (WebClientResponseException e) {
            cleanupCreatedCompany(companyId, e);
            throw apiFailure(operation, e);
        } catch (Exception e) {
            cleanupCreatedCompany(companyId, e);
            throw callFailure(operation, e);
        }
    }

    private boolean canReceiveByDefault(String peppolId) {
        try {
            checkRecipientAvailable(peppolId);
            return true;
        } catch (RuntimeException e) {
            // Sending can start while another provider receives, or while a lookup is unavailable.
            log.info("Registering {} for sending only: {}", peppolId, e.getMessage());
            return false;
        }
    }

    private void checkRecipientAvailable(String peppolId) {
        JsonNode response = recommandWebClient.post().uri("/verify")
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("peppolAddress", peppolId))
                .retrieve().bodyToMono(JsonNode.class).blockOptional()
                .orElseThrow(() -> new IllegalStateException("Empty Recommand recipient lookup"));
        if (!response.path("success").asBoolean() || !response.path("isValid").isBoolean()) {
            throw new IllegalStateException("Recommand recipient lookup failed");
        }
        if (response.path("isValid").asBoolean()) {
            throw new AlreadyRegisteredException(response.path("smpUrl").asText("another Peppol provider"));
        }
    }

    @Override
    public PeppolSettings getSettings(Registry registry) {
        loadReceiving(registry);
        return new PeppolSettings(registry.getReceiveDocuments());
    }

    @Override
    @Transactional(noRollbackFor = RuntimeException.class)
    public PeppolSettings updateSettings(Registry registry, PeppolSettings settings) {
        String companyId = parseVariables(registry.getVariables()).companyId();
        loadReceiving(registry);
        if (Boolean.TRUE.equals(settings.receiveDocuments()) && !registry.getReceiveDocuments()) {
            checkRecipientAvailable(registry.getPeppolId());
        }
        if (settings.receiveDocuments() != null
                && !settings.receiveDocuments().equals(registry.getReceiveDocuments())) {
            updateReceiving(companyId, settings.receiveDocuments(), registry);
            registry.setReceiveDocuments(settings.receiveDocuments());
        }
        return new PeppolSettings(registry.getReceiveDocuments());
    }

    private void loadReceiving(Registry registry) {
        if (registry.getReceiveDocuments() == null) {
            String companyId = parseVariables(registry.getVariables()).companyId();
            registry.setReceiveDocuments(getCompany(companyId).path("isSmpRecipient").asBoolean());
        }
    }

    private JsonNode getCompany(String companyId) {
        JsonNode response = recommandWebClient.get().uri("/companies/{companyId}", companyId)
                .retrieve().bodyToMono(JsonNode.class).blockOptional()
                .orElseThrow(() -> new IllegalStateException("Empty Recommand company response"));
        requireSuccess(response, "get company");
        JsonNode company = response.path("company");
        if (!company.path("isSmpRecipient").isBoolean()) {
            throw new IllegalStateException("Recommand company has no receiving setting");
        }
        return company;
    }

    private void createDocumentTypes(String companyId) {
        for (String docTypeId : List.of(INVOICE, CREDIT_NOTE, SELF_BILLING_INVOICE, SELF_BILLING_CREDIT_NOTE)) {
            String processId = docTypeId.contains("poacc:selfbilling:") ? SELF_BILLING_PROCESS : BILLING_PROCESS;
            JsonNode response = recommandWebClient.post().uri("/companies/{companyId}/document-types", companyId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("docTypeId", docTypeId, "processId", processId))
                    .retrieve().bodyToMono(JsonNode.class).blockOptional()
                    .orElseThrow(() -> new IllegalStateException("Empty Recommand create document type response"));
            requireSuccess(response, "create document type");
        }
    }

    private void createIdentifier(String companyId, PeppolIdentifier identifier) {
        JsonNode response = recommandWebClient.post().uri("/companies/{companyId}/identifiers", companyId)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("scheme", identifier.scheme(), "identifier", identifier.value()))
                .retrieve().bodyToMono(JsonNode.class).blockOptional()
                .orElseThrow(() -> new IllegalStateException("Empty Recommand create identifier response"));
        requireSuccess(response, "create identifier");
    }

    private void updateReceiving(String companyId, boolean receive, Registry registry) {
        registry.setReceiveDocuments(null);
        JsonNode response = recommandWebClient.put().uri("/companies/{companyId}", companyId)
                .contentType(MediaType.APPLICATION_JSON).bodyValue(Map.of("isSmpRecipient", receive)).retrieve()
                .onStatus(status -> status.value() == 409, error -> error.releaseBody()
                        .thenReturn(new AlreadyRegisteredException("another Peppol provider")))
                .bodyToMono(JsonNode.class).blockOptional()
                .orElseThrow(() -> new IllegalStateException("Empty Recommand update company response"));
        requireSuccess(response, "update company");
    }

    private void requireSuccess(JsonNode response, String operation) {
        if (!response.path("success").asBoolean()) {
            throw new IllegalStateException("Recommand did not " + operation);
        }
    }

    /// Completes company activation using the signed contract.
    private void verifyCompanyByContract(String companyId, byte[] signedContract) {
        MultipartBodyBuilder multipart = new MultipartBodyBuilder();
        multipart.part("contract", new ByteArrayResource(signedContract))
                .filename("contract_signed.pdf")
                .contentType(MediaType.APPLICATION_PDF);
        VerifyCompanyResponse response = recommandWebClient.post()
                .uri("/companies/{companyId}/verify-by-contract", companyId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .bodyValue(multipart.build())
                .retrieve().bodyToMono(VerifyCompanyResponse.class).blockOptional()
                .orElseThrow(() -> new IllegalStateException("Empty response from Recommand verify company by contract"));
        if (!response.success()) throw new IllegalStateException("Recommand did not verify the company by contract");
    }

    private void cleanupCreatedCompany(String companyId, Exception failure) {
        if (companyId != null) {
            try {
                recommandWebClient.delete()
                        .uri("/companies/{companyId}", companyId)
                        .retrieve()
                        .toBodilessEntity()
                        .block();
            } catch (Exception cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
                log.error("Failed to remove Recommand company {} after registration failed", companyId, cleanupFailure);
            }
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
    @Transactional(noRollbackFor = SendAuthorizationException.class)
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
            if (e.getStatusCode().value() == 401) {
                throw new SendAuthorizationException(companyMissing(variables.companyId()), e);
            }
            throw apiFailure("send document", e);
        } catch (Exception e) {
            throw callFailure("send document", e);
        }
    }

    private boolean companyMissing(String companyId) {
        try {
            recommandWebClient.get()
                    .uri("/companies/{companyId}", companyId)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(10))
                    .block();
        } catch (WebClientResponseException e) {
            // A 401 here can mean invalid shared credentials. Only a 404 confirms a missing company.
            if (e.getStatusCode().value() == 404) {
                return true;
            }
            log.warn("Recommand company lookup for {} returned HTTP {}; registration retained",
                    companyId, e.getStatusCode().value());
        } catch (Exception e) {
            log.warn("Recommand company lookup for {} failed; registration retained ({})",
                    companyId, e.getClass().getSimpleName());
        }
        return false;
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

package org.letspeppol.proxy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.proxy.dto.RegistrationRequest;
import org.letspeppol.proxy.dto.StatusReport;
import org.letspeppol.proxy.dto.scrada.*;
import org.letspeppol.proxy.exception.AlreadyRegisteredException;
import org.letspeppol.proxy.exception.ServiceUnavailableException;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.UblDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Transactional
@Service
public class ScradaService implements AccessPointServiceInterface {

    public static final String PARTICIPANT_SCHEME = "iso6523-actorid-upis";
    public static final String INVOICES_SCHEME = "busdox-docid-qns";
    public static final String INVOICES_VALUE = "urn:oasis:names:specification:ubl:schema:xsd:Invoice-2::Invoice##urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0::2.1";
    public static final String CREDIT_NOTES_SCHEME = "busdox-docid-qns";
    public static final String CREDIT_NOTES_VALUE = "urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2::CreditNote##urn:cen.eu:en16931:2017#compliant#urn:fdc:peppol.eu:2017:poacc:billing:3.0::2.1";
    public static final String PROCESS_SCHEME = "cenbii-procid-ubl";
    public static final String PROCESS_VALUE = "urn:fdc:peppol.eu:2017:poacc:billing:01:1.0";

    private final UblDocumentReceiverService ublDocumentReceiverService;
    @Qualifier("scradaWebClient")
    private final WebClient scradaWebClient;
    private final Counter registerCounter;
    private final Counter unregisterCounter;
    private final Counter documentSendCounter;
    private final Counter documentReceivedCounter;
    private final ObjectMapper objectMapper;

    @Override
    public AccessPoint getType() {
        return AccessPoint.SCRADA;
    }

    /// DOCS : [Scrada : Register company](https://www.scrada.be/api-documentation/#tag/Peppol-inbound/paths/~1v1~1company~1%7BcompanyID%7D~1peppol~1register/post)
    @Override
    public Map<String, Object> register(String peppolId, RegistrationRequest data) {
        try {
            RegisterRequest registerRequest = new RegisterRequest(
                    new ParticipantIdentifier(
                            PARTICIPANT_SCHEME,
                            peppolId
                    ),
                    new BusinessEntity(
                            data.name(),
                            data.language(),
                            data.country()
                    ),
                    List.of(
                            new DocumentType(
                                    INVOICES_SCHEME,
                                    INVOICES_VALUE,
                                    new ProcessIdentifier(
                                            PROCESS_SCHEME,
                                            PROCESS_VALUE
                                    )
                            ),
                            new DocumentType(
                                    CREDIT_NOTES_SCHEME,
                                    CREDIT_NOTES_VALUE,
                                    new ProcessIdentifier(
                                            PROCESS_SCHEME,
                                            PROCESS_VALUE
                                    )
                            )
                    ),
                    null
            );
            log.info("Scrada API request to {} with {}", "/register", objectMapper.writeValueAsString(registerRequest));
            String uuid = scradaWebClient
                    .post()
                    .uri("/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(registerRequest)
                    .retrieve()
                    .bodyToMono(String.class)
                    .blockOptional()
                    .orElseThrow(() -> new IllegalStateException("Empty response from Scrada register company"));

            registerCounter.increment();
            return Map.of("uuid", uuid);
        } catch (WebClientResponseException e) { // HTTP error (non-2xx)
            String body = e.getResponseBodyAsString();
            ErrorResponse errorResponse = null;
            try {
                errorResponse = objectMapper.readValue(body, ErrorResponse.class);
            } catch (Exception parseFail) {
                log.error("Scrada register API error {} {}: {} & parsing failed {}", e.getRawStatusCode(), e.getStatusText(), body, parseFail.getMessage(), e);
                throw new RuntimeException("Scrada API error: " + e.getStatusCode(), e);
            }
            log.warn("Scrada register API could not succeed (code={} type={}) {}: {}", errorResponse.errorCode(), errorResponse.errorType(), errorResponse.defaultFormat(), body);
            throw processErrorResponse(errorResponse);
        } catch (Exception e) { // timeouts, connection issues, deserialization errors, etc.
            log.error("Scrada register API call error {}", e.toString(), e);
            throw new RuntimeException("Failed to call Scrada API", e);
        }
    }

    private RuntimeException processErrorResponse(ErrorResponse errorResponse) {
        return switch (errorResponse.errorCode()) {
            case 110554 -> new AlreadyRegisteredException(errorResponse.parameters().get(1));
            case 100008 -> processErrorResponse(errorResponse.innerErrors().getFirst());
            case 110552 -> new AlreadyRegisteredException("scrada.be");
            case 100030 -> new ServiceUnavailableException(errorResponse.defaultFormat());
            default -> new IllegalStateException("Unexpected value: " + errorResponse.errorCode());
        };
    }

    /// DOCS : [Scrada : Deregister company](https://www.scrada.be/api-documentation/#tag/Peppol-inbound/paths/~1v1~1company~1%7BcompanyID%7D~1peppol~1deregister~1%7BparticipantIdentifierScheme%7D~1%7BparticipantIdentifierValue%7D/delete)
    @Override
    public void unregister(String peppolId, Map<String, Object> variables) {
//        String uuid = Objects.toString(variables.get("uuid"), null);
//        if (uuid == null || uuid.isEmpty()) {
//            throw new IllegalStateException("Cannot unregister an empty uuid");
//        }
        try {
            String uuid = scradaWebClient
                .delete()
                .uri("/deregister/{participantIdentifierScheme}/{participantIdentifierValue}", PARTICIPANT_SCHEME, peppolId)
                .retrieve()
                .bodyToMono(String.class)
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException("Empty response from Scrada register company"));
            if (!uuid.equals(variables.get("uuid"))) {
                log.warn("Scrada unregister API for account {} gave different uuid {} than during registration {}", peppolId, uuid, variables.get("uuid"));
            }
            unregisterCounter.increment();
        } catch (WebClientResponseException e) { // HTTP error (non-2xx)
            log.error("Scrada unregister API error {} {}: {}", e.getRawStatusCode(), e.getStatusText(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("Scrada API error: " + e.getStatusCode(), e);
        } catch (Exception e) { // timeouts, connection issues, deserialization errors, etc.
            log.error("Scrada unregister API call error {}", e.toString(), e);
            throw new RuntimeException("Failed to call Scrada API", e);
        }
    }

    /// DOCS : [Scrada : Send document](https://www.scrada.be/api-documentation/#tag/Peppol-outbound/paths/~1v1~1company~1%7BcompanyID%7D~1peppol~1outbound~1document/post)
    @Override
    public String sendDocument(UblDocument ublDocument) {
        try {
            String uuid = scradaWebClient
                    .post()
                    .uri("/outbound/document")
                    .contentType(MediaType.APPLICATION_XML)
                    .header("x-scrada-peppol-sender-scheme", PARTICIPANT_SCHEME)
                    .header("x-scrada-peppol-sender-id", ublDocument.getOwnerPeppolId())
                    .header("x-scrada-peppol-receiver-scheme", PARTICIPANT_SCHEME)
                    .header("x-scrada-peppol-receiver-id", ublDocument.getPartnerPeppolId())
                    .header("x-scrada-peppol-c1-country-code", "BE") //This is Peppol Corner Stone 1 and always Belgium for Scrada
                    .header("x-scrada-peppol-document-type-scheme", ublDocument.getType() == org.letspeppol.proxy.model.DocumentType.INVOICE ? INVOICES_SCHEME : CREDIT_NOTES_SCHEME)
                    .header("x-scrada-peppol-document-type-value", ublDocument.getType() == org.letspeppol.proxy.model.DocumentType.INVOICE ? INVOICES_VALUE : CREDIT_NOTES_VALUE)
                    .header("x-scrada-peppol-process-scheme", PROCESS_SCHEME)
                    .header("x-scrada-peppol-process-value", PROCESS_VALUE)
                    .header("x-scrada-external-reference", ublDocument.getId().toString()) //This is useful for Scrada debugging in https://scrada.be/en/company/{CompanyId}/settings/integrations/peppol/peppol-history
                    .bodyValue(ublDocument.getUbl())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .map(JsonNode::asText)
                    .blockOptional()
                    .orElseThrow(() -> new IllegalStateException("Empty response from Scrada send document"));
            documentSendCounter.increment();
            return uuid;
        } catch (WebClientResponseException e) { // HTTP error (non-2xx)
            log.error("Scrada outbound API error {} {}: {}", e.getRawStatusCode(), e.getStatusText(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("Scrada outbound API error: " + e.getStatusCode(), e);
        } catch (Exception e) { // timeouts, connection issues, deserialization errors, etc.
            log.error("Scrada outbound API call error {}", e.toString(), e);
            throw new RuntimeException("Failed to call Scrada API", e);
        }
    }

    /// DOCS : [Scrada : Get outbound document status](https://www.scrada.be/api-documentation/#tag/Peppol-outbound/paths/~1v1~1company~1{companyID}~1peppol~1outbound~1document~1{documentID}~1info/get)
    @Override
    public StatusReport getStatus(UblDocument ublDocument) {
        System.out.print("?");
        try {
            OutboundDocument outboundDocument = scradaWebClient
                    .get()
                    .uri("/outbound/document/{documentID}/info", ublDocument.getAccessPointId())
                    .retrieve()
                    .bodyToMono(OutboundDocument.class)
                    .blockOptional()
                    .orElseThrow(() -> new IllegalStateException("Empty response from Scrada get unconfirmed inbound documents"));
            return switch (outboundDocument.status()) {
                case "Created" -> null;
                case "Processed" -> new StatusReport(true, null);
                case "Retry" -> {
                    log.info("Scrada tried {} time(s) to send {} with feedback {}", outboundDocument.attempt(), outboundDocument.externalReference(), outboundDocument.errorMessage());
                    yield null;
                }
                case "Error" -> new StatusReport(false, outboundDocument.status() + " : " + outboundDocument.errorMessage());
                default -> new StatusReport(false, outboundDocument.status());
            };
        } catch (WebClientResponseException e) { // HTTP error (non-2xx)
            log.error("Scrada outbound status API error {} {}: {}", e.getRawStatusCode(), e.getStatusText(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("Scrada API error: " + e.getStatusCode(), e);
        } catch (Exception e) { // timeouts, connection issues, deserialization errors, etc.
            log.error("Scrada outbound status API call error {}", e.toString(), e);
            throw new RuntimeException("Failed to call Scrada API", e);
        }
    }

    @Override
    public void updateStatus(String id, String status) {

    }

    @Override
    public void receiveDocument(UblDocument ublDocument) {

    }

    /// DOCS : [Scrada : Get unconfirmed inbound documents](https://www.scrada.be/api-documentation/#tag/Peppol-inbound/paths/~1v1~1company~1{companyID}~1peppol~1inbound~1document~1unconfirmed/get)
    /// DOCS : [Scrada : Get inbound document](https://www.scrada.be/api-documentation/#tag/Peppol-inbound/paths/~1v1~1company~1{companyID}~1peppol~1inbound~1document~1{documentID}/get)
    /// DOCS : [Scrada : Confirm inbound document](https://www.scrada.be/api-documentation/#tag/Peppol-inbound/paths/~1v1~1company~1%7BcompanyID%7D~1peppol~1inbound~1document~1%7BdocumentID%7D~1confirm/put)
    @Override
    public void receiveDocuments() {
        System.out.print("?");
        try {
            UnconfirmedInboundDocuments unconfirmedInboundDocuments = scradaWebClient
                    .get()
                    .uri("/inbound/document/unconfirmed")
                    .retrieve()
                    .bodyToMono(UnconfirmedInboundDocuments.class)
                    .blockOptional()
                    .orElseThrow(() -> new IllegalStateException("Empty response from Scrada get unconfirmed inbound documents"));

            for (InboundDocument inboundDocument : unconfirmedInboundDocuments.results()) {
                log.debug("Received document {} from {} to {}", inboundDocument.id(), inboundDocument.peppolSenderID(), inboundDocument.peppolReceiverID());
                documentReceivedCounter.increment();
                String ubl = scradaWebClient
                        .get()
                        .uri("/inbound/document/{documentID}", inboundDocument.id())
                        .retrieve()
                        .bodyToMono(String.class)
                        .blockOptional()
                        .orElseThrow(() -> new IllegalStateException("Empty response from Scrada get inbound document"));

                ublDocumentReceiverService.createAsReceived(
                        inboundDocument.peppolDocumentTypeValue().equals(INVOICES_VALUE) ? org.letspeppol.proxy.model.DocumentType.INVOICE : org.letspeppol.proxy.model.DocumentType.CREDIT_NOTE,
                        inboundDocument.peppolSenderID(),
                        inboundDocument.peppolReceiverID(),
                        ubl,
                        AccessPoint.SCRADA,
                        inboundDocument.id(),
                        () -> {
                            scradaWebClient
                                    .put()
                                    .uri("/inbound/document/{documentID}/confirm", inboundDocument.id())
                                    .retrieve()
                                    .toBodilessEntity()
                                    .block();
                        }
                );
            }
        } catch (WebClientResponseException e) { // HTTP error (non-2xx)
            log.error("Scrada inbound API error {} {}: {}", e.getRawStatusCode(), e.getStatusText(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("Scrada API error: " + e.getStatusCode(), e);
        } catch (Exception e) { // timeouts, connection issues, deserialization errors, etc.
            log.error("Scrada inbound API call error {}", e.toString(), e);
            throw new RuntimeException("Failed to call Scrada API", e);
        }
    }

    /// NOT IMPLEMENTED
    /// DOCS : [Scrada : Get PDF of inbound document](https://www.scrada.be/api-documentation/#tag/Peppol-inbound/paths/~1v1~1company~1{companyID}~1peppol~1inbound~1document~1{documentID}~1pdf/get)
    /// DOCS : [Scrada : Send sales invoice](https://www.scrada.be/api-documentation/#tag/Peppol/paths/~1v1~1company~1{companyID}~1peppol~1lookup/post)
    /// DOCS : [Scrada : Send self-billing invoice](https://www.scrada.be/api-documentation/#tag/Peppol-outbound/paths/~1v1~1company~1{companyID}~1peppol~1outbound~1selfBillingInvoice/post)
    /// DOCS : [Scrada : Get outbound document](https://www.scrada.be/api-documentation/#tag/Peppol-outbound/paths/~1v1~1company~1{companyID}~1peppol~1outbound~1document~1{documentID}~1ubl/get)
    /// DOCS : [Scrada : Participant lookup](https://www.scrada.be/api-documentation/#tag/Peppol/paths/~1v1~1company~1{companyID}~1peppol~1lookup~1{scheme}~1{id}/get)
    /// DOCS : [Scrada : Party lookup (JSON)](https://www.scrada.be/api-documentation/#tag/Peppol/paths/~1v1~1company~1{companyID}~1peppol~1lookup/post)

}

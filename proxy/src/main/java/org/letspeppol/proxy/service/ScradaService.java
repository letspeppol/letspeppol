package org.letspeppol.proxy.service;

import lombok.RequiredArgsConstructor;
import org.letspeppol.proxy.dto.RegistrationRequest;
import org.letspeppol.proxy.dto.scrada.*;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.UblDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.util.List;
import java.util.Map;

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

    @Lazy
    @Autowired
    private UblDocumentService ublDocumentService; //TODO : Is it possible to not have a circular dependency
    @Qualifier("scradaWebClient")
    private final WebClient scradaWebClient;

    @Override
    public AccessPoint getType() {
        return AccessPoint.SCRADA;
    }

    /// DOCS : [Scrada : Register company](https://www.scrada.be/api-documentation/#tag/Peppol-inbound/paths/~1v1~1company~1%7BcompanyID%7D~1peppol~1register/post)
    @Override
    public Map<String, Object> register(String peppolId, RegistrationRequest data) {
        try {
            String uuid = scradaWebClient
                    .post()
                    .uri("/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(new RegisterRequest(
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
                    ))
                    .retrieve()
                    .bodyToMono(String.class)
                    .blockOptional()
                    .orElseThrow(() -> new IllegalStateException("Empty response from Scrada register company"));

            return Map.of("uuid", uuid);
        } catch (WebClientResponseException e) { // HTTP error (non-2xx)
            throw new RuntimeException("Scrada API error: " + e.getStatusCode(), e);
        } catch (Exception e) { // timeouts, connection issues, deserialization errors, etc.
            throw new RuntimeException("Failed to call Scrada API", e);
        }
    }

    /// DOCS : [Scrada : Deregister company](https://www.scrada.be/api-documentation/#tag/Peppol-inbound/paths/~1v1~1company~1%7BcompanyID%7D~1peppol~1deregister~1%7BparticipantIdentifierScheme%7D~1%7BparticipantIdentifierValue%7D/delete)
    @Override
    public void unregister(String peppolId, Map<String, Object> variables) {
//        String uuid = Objects.toString(variables.get("uuid"), null);
//        if (uuid == null || uuid.isEmpty()) {
//            throw new IllegalStateException("Cannot unregister an empty uuid");
//        }
        try {
            scradaWebClient
                .delete()
                .uri("/deregister/{participantIdentifierScheme}/{participantIdentifierValue}", PARTICIPANT_SCHEME, peppolId)
                .retrieve()
                .toBodilessEntity()
                .block();

        } catch (WebClientResponseException e) { // HTTP error (non-2xx)
            throw new RuntimeException("e-invoice API error: " + e.getStatusCode(), e);
        } catch (Exception e) { // timeouts, connection issues, deserialization errors, etc.
            throw new RuntimeException("Failed to call e-invoice API", e);
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
                    .header("x-scrada-peppol-c1-country-code", "BE") //TODO : Where to get the country code of sender ?
                    .header("x-scrada-peppol-document-type-scheme", INVOICES_SCHEME) //TODO : We need to extract TYPE !
                    .header("x-scrada-peppol-document-type-value", INVOICES_VALUE)
                    .header("x-scrada-peppol-process-scheme", PROCESS_SCHEME)
                    .header("x-scrada-peppol-process-value", PROCESS_VALUE)
                    //.header("x-scrada-external-reference", "V1/202400512") //TODO : We could add InvoiceReference
                    .bodyValue(ublDocument.getUbl())
                    .retrieve()
                    .bodyToMono(String.class)
                    .blockOptional()
                    .orElseThrow(() -> new IllegalStateException("Empty response from Scrada send document"));

            return uuid;
        } catch (WebClientResponseException e) { // HTTP error (non-2xx)
            throw new RuntimeException("Scrada API error: " + e.getStatusCode(), e);
        } catch (Exception e) { // timeouts, connection issues, deserialization errors, etc.
            throw new RuntimeException("Failed to call Scrada API", e);
        }
    }

    @Override
    public void updateStatus(String id, String status) {

    }

    @Override
    public void receiveDocument(UblDocument ublDocument) {

    }

    @Override
    public void receiveDocuments() {
        try {
            UnconfirmedInboundDocuments unconfirmedInboundDocuments = scradaWebClient
                    .get()
                    .uri("/inbound/document/unconfirmed")
                    .retrieve()
                    .bodyToMono(UnconfirmedInboundDocuments.class)
                    .blockOptional()
                    .orElseThrow(() -> new IllegalStateException("Empty response from Scrada get unconfirmed inbound documents"));

            for (InboundDocument inboundDocument : unconfirmedInboundDocuments.results()) {
                String ubl = scradaWebClient
                        .get()
                        .uri("/inbound/document/{documentID}", inboundDocument.id())
                        .retrieve()
                        .bodyToMono(String.class)
                        .blockOptional()
                        .orElseThrow(() -> new IllegalStateException("Empty response from Scrada get inbound document"));

                ublDocumentService.createAsReceived(
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
            throw new RuntimeException("Scrada API error: " + e.getStatusCode(), e);
        } catch (Exception e) { // timeouts, connection issues, deserialization errors, etc.
            throw new RuntimeException("Failed to call Scrada API", e);
        }
    }
}

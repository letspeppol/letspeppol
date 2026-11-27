package org.letspeppol.proxy.service;

import org.letspeppol.proxy.util.PhossEnvelope;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/** Read-only Peppol SMP metadata access. AP discovery/validation remains authoritative. */
public class PhossSmpClient {
    private final WebClient client;

    public PhossSmpClient(@Qualifier("phossSmpWebClient") WebClient client) {
        this.client = client;
    }

    public String getInvoiceMetadata(String participant) {
        PhossEnvelope.requireParticipant(participant);
        String path = "/" + encode("iso6523-actorid-upis::" + participant)
                + "/services/" + encode("busdox-docid-qns::" + PhossEnvelope.DOCUMENT_ID);
        try {
            // A pre-encoded path avoids treating '#' as a fragment or encoding '%' twice.
            String metadata = client.get().uri(builder -> java.net.URI.create(builder.build().toString().replaceAll("/+$", "") + path))
                    .retrieve().bodyToMono(String.class).block();
            if (metadata == null || metadata.isBlank()) throw new IllegalStateException("Empty SMP metadata");
            return metadata;
        } catch (WebClientResponseException.NotFound e) {
            return null;
        } catch (RuntimeException e) {
            throw new IllegalStateException("PHOSS SMP lookup unavailable");
        }
    }

    private static String encode(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8).replace(":", "%3A");
    }
}

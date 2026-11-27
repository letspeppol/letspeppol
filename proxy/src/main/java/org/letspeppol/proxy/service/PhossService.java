package org.letspeppol.proxy.service;

import lombok.extern.slf4j.Slf4j;
import org.letspeppol.proxy.dto.PeppolSettings;
import org.letspeppol.proxy.dto.RegistrationRequest;
import org.letspeppol.proxy.dto.StatusReport;
import org.letspeppol.proxy.dto.recommand.PeppolReceipt;
import org.letspeppol.proxy.exception.ConflictException;
import org.letspeppol.proxy.exception.SendDocumentException;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.Registry;
import org.letspeppol.proxy.model.UblDocument;
import org.letspeppol.proxy.repository.RegistryRepository;
import org.letspeppol.proxy.util.HashUtil;
import org.letspeppol.proxy.util.PhossEnvelope;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class PhossService implements AccessPointServiceInterface {
    private final WebClient ap;
    private final PhossSubmissionStore submissions;
    private final RegistryRepository registries;
    private final ObjectMapper json;
    private final PhossSmpClient smp;

    public PhossService(@Qualifier("phossApWebClient") WebClient ap, PhossSubmissionStore submissions,
                        RegistryRepository registries, ObjectMapper json, PhossSmpClient smp) {
        this.ap = ap;
        this.submissions = submissions;
        this.registries = registries;
        this.json = json;
        this.smp = smp;
    }

    @Override public AccessPoint getType() { return AccessPoint.PHOSS; }

    @Override
    public void validateRegistration(String peppolId, RegistrationRequest data) {
        PhossEnvelope.requireParticipant(peppolId);
        if (data == null || data.country() == null || !data.country().matches("[A-Z]{2}")) {
            throw new IllegalArgumentException("PHOSS requires an uppercase two-letter C1 country");
        }
    }

    /** This is a local send-only association, not an SMP registration or public Peppol activation. */
    @Override
    public void register(Registry registry, RegistrationRequest data) {
        validateRegistration(registry.getPeppolId(), data);
        registry.setVariables(Map.of("c1Country", data.country(), "registrationMode", "send-only"));
        registry.setReceiveDocuments(false);
    }

    @Override public void unregister(String peppolId, Map<String, Object> variables) {
        // Only the caller's local association is removed. No remote metadata is owned by this adapter.
    }

    @Override public PeppolSettings getReceivingAvailability(String peppolId) {
        return new PeppolSettings(false, smp.getInvoiceMetadata(peppolId) == null ? null : "Configured PHOSS SMP");
    }

    @Override public PeppolSettings getSettings(Registry registry) { return new PeppolSettings(false); }

    @Override public PeppolSettings updateSettings(Registry registry, PeppolSettings settings) {
        if (Boolean.TRUE.equals(settings.receiveDocuments())) {
            throw new ConflictException("PHOSS inbound forwarding and SMP administration are not configured");
        }
        registry.setReceiveDocuments(false);
        return new PeppolSettings(false);
    }

    @Override
    public String sendDocument(UblDocument document) {
        Registry sender = registries.findById(document.getOwnerPeppolId())
                .orElseThrow(() -> new SendDocumentException("PHOSS sender has no local registration", null));
        Object country = sender.getVariables() == null ? null : sender.getVariables().get("c1Country");
        if (sender.getAccessPoint() != AccessPoint.PHOSS || country == null) {
            throw new SendDocumentException("PHOSS sender is not configured for sending", null);
        }
        // Validate before claiming an attempt. The fingerprint binds the durable ID to immutable content.
        String fingerprint = HashUtil.sha256(document.getUbl() + "\n" + document.getOwnerPeppolId()
                + "\n" + document.getPartnerPeppolId() + "\n" + country);
        final PhossSubmissionStore.Submission saved;
        try {
            PhossEnvelope.create(document, document.getId().toString(), document.getCreatedOn(), country.toString());
            saved = submissions.prepare(document.getId(), fingerprint);
        } catch (IllegalArgumentException e) {
            throw new SendDocumentException(e.getMessage(), null);
        }
        if (saved.failure() != null) throw new SendDocumentException(saved.failure(), null);
        String sbd = PhossEnvelope.create(document, saved.instanceId(), saved.createdOn(), country.toString());
        if (!submissions.claim(document.getId())) return saved.instanceId();

        // The claim is committed before HTTP. Never POST again after an ambiguous outcome or crash.
        try {
            String response = ap.post().uri("/api/outbound/submit-sbd")
                    .contentType(org.springframework.http.MediaType.APPLICATION_XML).bodyValue(sbd)
                    .retrieve().bodyToMono(String.class).block();
            saveReport(document, saved.instanceId(), response);
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 422) {
                // PHOSS may already have persisted this transaction and scheduled its own retry.
                saveReport(document, saved.instanceId(), e.getResponseBodyAsString());
            } else if (status == 400 || status == 401 || status == 403 || status == 404) {
                String reason = "PHOSS submission rejected (HTTP " + status + ")";
                submissions.fail(document.getId(), reason);
                throw new SendDocumentException(reason, null);
            } else {
                log.warn("PHOSS submission outcome uncertain for {}; reconciling status, not resubmitting", document.getId());
            }
        } catch (WebClientRequestException e) {
            log.warn("PHOSS submission connection failed for {}; reconciling status, not resubmitting", document.getId());
        } catch (RuntimeException e) {
            // Response decoding/storage can fail after delivery too. The committed claim still prevents replay.
            log.warn("PHOSS submission response unavailable for {}; reconciling status, not resubmitting", document.getId());
        }
        return saved.instanceId();
    }

    private void saveReport(UblDocument document, String instanceId, String response) {
        try {
            JsonNode report = json.readTree(response);
            if (!instanceId.equals(report.path("sbdhInstanceIdentifier").asText())) return;
            Map<String, Object> safe = new LinkedHashMap<>();
            for (String key : new String[]{"as4MessageId", "as4ReceivedSignalMsg", "sendingResult", "sendingSuccess", "overallSuccess"}) {
                JsonNode value = report.get(key);
                if (value != null && (value.isString() || value.isBoolean())) {
                    safe.put(key, value.isBoolean() ? value.asBoolean() : value.asText());
                }
            }
            submissions.report(document.getId(), json.writeValueAsString(safe));
        } catch (RuntimeException e) {
            log.warn("PHOSS report could not be recorded for {}; status reconciliation remains active", document.getId());
        }
    }

    @Override
    public StatusReport getStatus(UblDocument document) {
        var saved = submissions.load(document.getId());
        if (saved.failure() != null) return new StatusReport(false, saved.failure());
        try {
            JsonNode status = ap.get().uri(b -> b.path("/api/outbound/status/{id}")
                            .queryParam("includeArchive", true).build(saved.instanceId()))
                    .retrieve().bodyToMono(JsonNode.class).block();
            if (status == null || !saved.instanceId().equals(status.path("sbdhInstanceID").asText())) return null;
            return switch (status.path("status").asText()) {
                case "sent" -> {
                    document.setAccessPointDetails(getDeliveryDetails(document));
                    yield new StatusReport(true, null);
                }
                case "rejected", "permanently_failed" -> new StatusReport(false,
                        "PHOSS transport " + status.path("status").asText() + "; contact support with SBDH ID " + saved.instanceId());
                default -> null; // Includes retryable failed, unknown states and incomplete responses.
            };
        } catch (RuntimeException e) {
            // Includes 404 after an uncertain submit: an operator must reconcile; don't invent success/failure.
            log.warn("PHOSS status unavailable for {}; no automatic resubmission", document.getId());
            return null;
        }
    }

    @Override
    public Map<String, Object> getDeliveryDetails(UblDocument document) {
        var saved = submissions.load(document.getId());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sbdhInstanceId", saved.instanceId());
        if (saved.report() != null) {
            JsonNode report = json.readTree(saved.report());
            String sentId = report.path("as4MessageId").asText("");
            var receipt = PeppolReceipt.parse(report.path("as4ReceivedSignalMsg").asText(""), sentId);
            if (!sentId.isBlank()) details.put("as4MessageId", sentId);
            if (receipt.messageId() != null) details.put("receiptMessageId", receipt.messageId());
            if (receipt.timestamp() != null) details.put("receiptTimestamp", receipt.timestamp().toString());
        }
        return details;
    }

    @Override public void updateStatus(String id, String status) {
        throw new UnsupportedOperationException("PHOSS uses authenticated status polling");
    }
    @Override public void receiveDocument(UblDocument document) {
        throw new UnsupportedOperationException("PHOSS inbound forwarding is not configured");
    }
    @Override public void receiveDocuments() { /* Outbound-only: preserve the lab's existing test backend. */ }
}

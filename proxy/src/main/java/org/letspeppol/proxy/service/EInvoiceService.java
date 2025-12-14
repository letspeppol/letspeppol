package org.letspeppol.proxy.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import org.letspeppol.proxy.dto.RegistrationRequest;
import org.letspeppol.proxy.dto.StatusReport;
import org.letspeppol.proxy.dto.e_invoice.*;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.UblDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import java.util.Map;

@RequiredArgsConstructor
@Transactional
@Service
public class EInvoiceService implements AccessPointServiceInterface {

    @Qualifier("eInvoiceWebClient")
    private final WebClient eInvoiceWebClient;
    @Qualifier("eInvoiceOrganisationWebClient")
    private final WebClient eInvoiceOrganisationWebClient;
    private final ObjectMapper objectMapper;
    private final Counter registerCounter;
    private final Counter unregisterCounter;

    @Override
    public AccessPoint getType() {
        return AccessPoint.E_INVOICE;
    }

    @Override
    public Map<String, Object> register(String peppolId, RegistrationRequest data) {
        try {
            TenantCreateResponse tenantCreateResponse = eInvoiceOrganisationWebClient
                .post()
                .uri("/tenants")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new TenantCreateRequest(peppolId, data.name())) //TODO : is description useful ?
                .retrieve()
                .bodyToMono(TenantCreateResponse.class)
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException("Empty response from e-invoice create tenant"));
            String tenantId = tenantCreateResponse.id();

            ApiKeyCreateResponse apiKeyCreateResponse = eInvoiceOrganisationWebClient
                .post()
                .uri("/tenants/"+tenantId+"/api-keys")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ApiKeyCreateRequest("LetsPeppol", null))
                .retrieve()
                .bodyToMono(ApiKeyCreateResponse.class)
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException("Empty response from e-invoice create API key"));
            String keyId = apiKeyCreateResponse.id();
            String key = apiKeyCreateResponse.key();

            RegisterPeppolResponse registerPeppolResponse = eInvoiceOrganisationWebClient
                .post()
                .uri("/tenants/"+tenantId+"/peppol/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new RegisterPeppolRequest(peppolId, data.name()))
                .retrieve()
                .bodyToMono(RegisterPeppolResponse.class)
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException("Empty response from e-invoice register tenant"));
            if (!registerPeppolResponse.registered()) {
                //TODO : error log
            }

            registerCounter.increment();
            Variables variables = new Variables(tenantId, keyId, key);
            return objectMapper.convertValue(variables, new TypeReference<>() {});
        } catch (WebClientResponseException e) { // HTTP error (non-2xx)
            throw new RuntimeException("e-invoice API error: " + e.getStatusCode(), e);
        } catch (Exception e) { // timeouts, connection issues, deserialization errors, etc.
            throw new RuntimeException("Failed to call e-invoice API", e);
        }
    }

    @Override
    public void unregister(String peppolId, Map<String, Object> variables) {
        Variables eInvoiceVariables = objectMapper.convertValue(variables, Variables.class);
        try {
            eInvoiceOrganisationWebClient
                .post()
                .uri("/tenants/"+eInvoiceVariables.tenantId()+"/peppol/unregister")
                .retrieve()
                .toBodilessEntity()
                .block();

            eInvoiceOrganisationWebClient
                .delete()
                .uri("/tenants/"+eInvoiceVariables.tenantId()+"/api-keys/"+eInvoiceVariables.keyId())
                .retrieve()
                .toBodilessEntity()
                .block();

            eInvoiceOrganisationWebClient
                .delete()
                .uri("/tenants/"+eInvoiceVariables.tenantId())
                .retrieve()
                .toBodilessEntity()
                .block();

            unregisterCounter.increment();
        } catch (WebClientResponseException e) { // HTTP error (non-2xx)
            throw new RuntimeException("e-invoice API error: " + e.getStatusCode(), e);
        } catch (Exception e) { // timeouts, connection issues, deserialization errors, etc.
            throw new RuntimeException("Failed to call e-invoice API", e);
        }
    }

    @Override
    public String sendDocument(UblDocument ublDocument) {
        return "docUuid";
    }

    @Override
    public StatusReport getStatus(UblDocument ublDocument) {
        return null;
    }

    @Override
    public void updateStatus(String id, String status) {

    }

    @Override
    public void receiveDocument(UblDocument ublDocument) {

    }

    @Override
    public void receiveDocuments() {

    }
}

package org.letspeppol.proxy.service;

import org.letspeppol.proxy.dto.RegistrationRequest;
import org.letspeppol.proxy.dto.PeppolSettings;
import org.letspeppol.proxy.exception.ConflictException;
import org.letspeppol.proxy.dto.StatusReport;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.UblDocument;
import org.letspeppol.proxy.model.Registry;
import java.util.Map;

public interface AccessPointServiceInterface {

    AccessPoint getType();

    /** Check registration requirements without remote changes, including requirements for restoring a registration. */
    void validateRegistration(String peppolId, RegistrationRequest data);

    void register(Registry registry, RegistrationRequest data);

    void unregister(String peppolId, Map<String, Object> variables);

    /** The caller persists settings changes on the locked registry entity. */
    default PeppolSettings getSettings(Registry registry) {
        throw new ConflictException("Company settings are not supported by this access point");
    }

    /** The caller persists cache updates or invalidation, including when a provider write fails. */
    default PeppolSettings updateSettings(Registry registry, PeppolSettings settings) {
        throw new ConflictException("Company settings are not supported by this access point");
    }

    String sendDocument(UblDocument ublDocument);

    StatusReport getStatus(UblDocument ublDocument);

    default Map<String, Object> getDeliveryDetails(UblDocument ublDocument) {
        return Map.of();
    }

    void updateStatus(String id, String status); //For webhooks

    void receiveDocument(UblDocument ublDocument); //For webhooks

    void receiveDocuments(); //For polling only AP

}

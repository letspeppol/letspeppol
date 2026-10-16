package org.letspeppol.proxy.service;

import org.letspeppol.proxy.dto.RegistrationRequest;
import org.letspeppol.proxy.dto.StatusReport;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.UblDocument;
import java.util.Map;

public interface AccessPointServiceInterface {

    AccessPoint getType();

    /** Check registration requirements without remote changes, including requirements for restoring a registration. */
    void validateRegistration(String peppolId, RegistrationRequest data);

    Map<String, Object> register(String peppolId, RegistrationRequest data);

    void unregister(String peppolId, Map<String, Object> variables);

    String sendDocument(UblDocument ublDocument);

    StatusReport getStatus(UblDocument ublDocument);

    default Map<String, Object> getDeliveryDetails(UblDocument ublDocument) {
        return Map.of();
    }

    void updateStatus(String id, String status); //For webhooks

    void receiveDocument(UblDocument ublDocument); //For webhooks

    void receiveDocuments(); //For polling only AP

}

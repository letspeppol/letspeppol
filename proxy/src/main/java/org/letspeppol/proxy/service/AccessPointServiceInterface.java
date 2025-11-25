package org.letspeppol.proxy.service;

import org.letspeppol.proxy.dto.RegistrationRequest;
import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.UblDocument;
import java.util.Map;

public interface AccessPointServiceInterface {

    AccessPoint getType();

    Map<String, Object> register(String peppolId, RegistrationRequest data);

    void unregister(String peppolId, Map<String, Object> variables);

    String sendDocument(UblDocument ublDocument);

    void updateStatus(String id, String status); //For webhooks

    void receiveDocument(UblDocument ublDocument); //For webhooks

//    List<UblDocument> receiveDocuments(); //For polling only AP

}

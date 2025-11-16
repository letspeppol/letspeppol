package org.letspeppol.proxy.service;

import org.letspeppol.proxy.model.AccessPoint;
import org.letspeppol.proxy.model.UblDocument;

import java.util.Map;

public interface AccessPointServiceInterface {

    AccessPoint getType();

    Map<String, Object> register(String peppolId, Map<String, Object> data);

    void unregister(String peppolId);

    String sendDocument(UblDocument ublDocument);

    void updateStatus(String id, String status); //For webhooks

    void receiveDocument(UblDocument ublDocument); //For webhooks

//    List<UblDocument> receiveDocuments(); //For polling only AP

}

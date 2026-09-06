package org.letspeppol.proxy.dto.recommand;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record InboxResponse(
        boolean success,
        List<InboxDocument> documents
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InboxDocument(
            String id,
            String direction,
            String senderId,
            String receiverId,
            String type,
            String readAt
    ) {}
}

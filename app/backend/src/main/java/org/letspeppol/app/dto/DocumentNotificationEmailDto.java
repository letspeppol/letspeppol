package org.letspeppol.app.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DocumentNotificationEmailDto extends EmailDto {
    private UUID documentId;

    public DocumentNotificationEmailDto(String from, String to, String cc, String bcc,
                                       String replyTo, String subject, String text,
                                       String html, UUID documentId) {
        super(from, to, cc, bcc, replyTo, subject, text, html);
        this.documentId = documentId;
    }
}

package org.letspeppol.proxy.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
public class AppLink {

    @Embeddable
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    @EqualsAndHashCode
    public static class AppLinkId implements Serializable {
        @Column(name = "peppol_id", nullable = false, columnDefinition = "text")
        private String peppolId;

        @Column(name = "linked_uid", nullable = false)
        private UUID linkedUid;
    }

    @EmbeddedId
    private AppLinkId id = new AppLinkId();

    @Transient
    public String getPeppolId() { return id != null ? id.getPeppolId() : null; }

    @Transient
    public UUID getLinkedUid() { return id != null ? id.getLinkedUid() : null; }
}

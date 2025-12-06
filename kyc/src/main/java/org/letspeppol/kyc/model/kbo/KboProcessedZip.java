package org.letspeppol.kyc.model.kbo;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "kbo_processed_zip", schema = "kyc")
@Getter
@Setter
@NoArgsConstructor
public class KboProcessedZip {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "filename", nullable = false, unique = true, length = 255)
    private String filename;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public KboProcessedZip(String filename, Instant processedAt) {
        this.filename = filename;
        this.processedAt = processedAt;
    }
}

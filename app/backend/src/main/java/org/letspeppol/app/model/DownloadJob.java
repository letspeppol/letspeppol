package org.letspeppol.app.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "download_job")
@Getter
@Setter
@NoArgsConstructor
public class DownloadJob extends GenericEntity {

    public enum Status {
        PENDING,
        PROCESSING,
        READY,
        FAILED
    }

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_download_job_company"))
    private Company company;

    @Column(nullable = false)
    private LocalDate fromDate;

    @Column(nullable = false)
    private LocalDate toDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Status status = Status.PENDING;

    private String archiveFilename;

    @Column(length = 1024)
    private String archivePath;
    private Instant completedOn;
    private Instant expiresOn;
    private Instant processingStartedOn;

    @Column(nullable = false)
    private int downloadCount;

    @Column(columnDefinition = "text")
    private String failureMessage;

    public DownloadJob(Company company, LocalDate fromDate, LocalDate toDate) {
        this.company = company;
        this.fromDate = fromDate;
        this.toDate = toDate;
    }
}

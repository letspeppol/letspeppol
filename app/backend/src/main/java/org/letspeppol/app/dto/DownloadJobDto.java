package org.letspeppol.app.dto;

import org.letspeppol.app.model.DownloadJob;

import java.time.Instant;
import java.time.LocalDate;

public record DownloadJobDto(
        Long id,
        LocalDate fromDate,
        LocalDate toDate,
        DownloadJob.Status status,
        String filename,
        Instant createdOn,
        Instant completedOn,
        Instant expiresOn,
        int downloadCount,
        String failureMessage
) {
}

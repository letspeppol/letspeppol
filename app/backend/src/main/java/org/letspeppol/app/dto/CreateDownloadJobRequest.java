package org.letspeppol.app.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateDownloadJobRequest(
        @NotNull LocalDate fromDate,
        @NotNull LocalDate toDate
) {
}

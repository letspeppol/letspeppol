package org.letspeppol.app.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.letspeppol.app.dto.CreateDownloadJobRequest;
import org.letspeppol.app.exception.ConflictException;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.DownloadJob;
import org.letspeppol.app.repository.CompanyRepository;
import org.letspeppol.app.repository.DocumentRepository;
import org.letspeppol.app.repository.DownloadJobRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DownloadJobServiceTest {

    @TempDir
    Path archiveDirectory;

    private CompanyRepository companyRepository;
    private DocumentRepository documentRepository;
    private DownloadJobRepository downloadJobRepository;
    private DownloadJobService service;
    private Company company;

    @BeforeEach
    void setUp() {
        companyRepository = mock(CompanyRepository.class);
        documentRepository = mock(DocumentRepository.class);
        downloadJobRepository = mock(DownloadJobRepository.class);
        service = new DownloadJobService(companyRepository, documentRepository, downloadJobRepository,
                mock(ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(service, "archiveDirectory", archiveDirectory.toString());
        company = new Company();
        company.setPeppolId("0208:owner");
        when(companyRepository.findByPeppolIdForUpdate("0208:owner")).thenReturn(Optional.of(company));
        when(downloadJobRepository.findAllByCompanyPeppolIdOrderByCreatedOnDesc("0208:owner")).thenReturn(java.util.List.of());
    }

    @Test
    void rejectsReversedAndCrossYearPeriodsBeforeWritingAnything() {
        assertThatThrownBy(() -> service.create("0208:owner", request("2026-02-02", "2026-02-01")))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.create("0208:owner", request("2025-12-31", "2026-01-01")))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(documentRepository, downloadJobRepository);
    }

    @Test
    void rejectsEmptyPeriodWithoutCreatingJob() {
        when(downloadJobRepository.countByCompanyAndStatusIn(eq(company), anyCollection())).thenReturn(0L);
        when(documentRepository.existsForArchive(eq("0208:owner"), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.create("0208:owner", request("2026-01-01", "2026-01-31")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No finalized documents");
        verify(downloadJobRepository, never()).save(any(DownloadJob.class));
    }

    @Test
    void enforcesFivePendingOrReadyJobsPerCompany() {
        when(downloadJobRepository.countByCompanyAndStatusIn(eq(company), anyCollection())).thenReturn(5L);

        assertThatThrownBy(() -> service.create("0208:owner", request("2026-01-01", "2026-01-31")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("five");
        verifyNoInteractions(documentRepository);
    }

    @Test
    void thirdDownloadIsReservedAndThenDeletesArchiveAndJob() throws Exception {
        ReflectionTestUtils.setField(company, "id", 12L);
        DownloadJob job = new DownloadJob(company, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        ReflectionTestUtils.setField(job, "id", 44L);
        job.setStatus(DownloadJob.Status.READY);
        job.setArchiveFilename("20260101-20260131.zip");
        job.setArchivePath(Path.of("12", "44", "20260101-20260131.zip").toString());
        job.setExpiresOn(Instant.now().plusSeconds(3600));
        job.setDownloadCount(2);
        Path archive = archiveDirectory.resolve(job.getArchivePath());
        Files.createDirectories(archive.getParent());
        Files.writeString(archive, "zip");
        when(downloadJobRepository.findByIdAndPeppolIdForUpdate(44L, "0208:owner")).thenReturn(Optional.of(job));
        when(downloadJobRepository.findByIdForUpdate(44L)).thenReturn(Optional.of(job));

        DownloadJobService.DownloadReservation reservation = service.reserveDownload("0208:owner", 44L);

        org.assertj.core.api.Assertions.assertThat(reservation.terminal()).isTrue();
        org.assertj.core.api.Assertions.assertThat(job.getDownloadCount()).isEqualTo(3);
        service.completeTerminalDownload(reservation);
        org.assertj.core.api.Assertions.assertThat(archive).doesNotExist();
        verify(downloadJobRepository).delete(job);
    }

    private static CreateDownloadJobRequest request(String from, String to) {
        return new CreateDownloadJobRequest(LocalDate.parse(from), LocalDate.parse(to));
    }
}

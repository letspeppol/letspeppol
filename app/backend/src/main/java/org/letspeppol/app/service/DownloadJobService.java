package org.letspeppol.app.service;

import lombok.RequiredArgsConstructor;
import org.letspeppol.app.dto.CreateDownloadJobRequest;
import org.letspeppol.app.dto.DownloadJobDto;
import org.letspeppol.app.events.DownloadJobCreatedEvent;
import org.letspeppol.app.exception.ConflictException;
import org.letspeppol.app.exception.NotFoundException;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.DocumentDirection;
import org.letspeppol.app.model.DownloadJob;
import org.letspeppol.app.repository.CompanyRepository;
import org.letspeppol.app.repository.DocumentRepository;
import org.letspeppol.app.repository.DownloadJobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DownloadJobService {

    private static final int MAX_ACTIVE_JOBS = 5;
    private static final int MAX_DOWNLOADS = 3;
    private static final Duration RETENTION = Duration.ofDays(7);
    private static final DateTimeFormatter ARCHIVE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final EnumSet<DownloadJob.Status> SLOT_STATUSES = EnumSet.of(
            DownloadJob.Status.PENDING,
            DownloadJob.Status.PROCESSING,
            DownloadJob.Status.READY
    );

    private final CompanyRepository companyRepository;
    private final DocumentRepository documentRepository;
    private final DownloadJobRepository downloadJobRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${download.archive-directory}")
    private String archiveDirectory;

    @Transactional
    public DownloadJobDto create(String peppolId, CreateDownloadJobRequest request) {
        validatePeriod(request.fromDate(), request.toDate());
        Company company = companyRepository.findByPeppolIdForUpdate(peppolId)
                .orElseThrow(() -> new NotFoundException("Company does not exist"));
        purgeExpiredForCompany(company);
        ensureSlotAvailable(company);

        DateBounds bounds = bounds(request.fromDate(), request.toDate());
        if (!documentRepository.existsForArchive(peppolId, bounds.startInclusive(), bounds.endExclusive())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "No finalized documents with UBL data exist in this period");
        }

        DownloadJob job = downloadJobRepository.save(new DownloadJob(company, request.fromDate(), request.toDate()));
        eventPublisher.publishEvent(new DownloadJobCreatedEvent(job.getId()));
        return toDto(job);
    }

    @Transactional
    public List<DownloadJobDto> findAll(String peppolId) {
        Company company = companyRepository.findByPeppolIdForUpdate(peppolId)
                .orElseThrow(() -> new NotFoundException("Company does not exist"));
        purgeExpiredForCompany(company);
        return downloadJobRepository.findAllByCompanyPeppolIdOrderByCreatedOnDesc(peppolId).stream()
                .map(DownloadJobService::toDto)
                .toList();
    }

    @Transactional
    public DownloadJobDto retry(String peppolId, Long id) {
        DownloadJob existingJob = downloadJobRepository.findByIdAndCompanyPeppolId(id, peppolId)
                .orElseThrow(() -> new NotFoundException("Download job does not exist"));
        if (existingJob.getStatus() != DownloadJob.Status.FAILED) {
            throw new ConflictException("Only failed download jobs can be retried");
        }

        Company company = companyRepository.findByPeppolIdForUpdate(peppolId)
                .orElseThrow(() -> new NotFoundException("Company does not exist"));
        purgeExpiredForCompany(company);
        ensureSlotAvailable(company);

        DownloadJob job = downloadJobRepository.findByIdAndPeppolIdForUpdate(id, peppolId)
                .orElseThrow(() -> new NotFoundException("Download job does not exist"));
        if (job.getStatus() != DownloadJob.Status.FAILED) {
            throw new ConflictException("Only failed download jobs can be retried");
        }
        job.setStatus(DownloadJob.Status.PENDING);
        job.setFailureMessage(null);
        job.setProcessingStartedOn(null);
        job.setCompletedOn(null);
        job.setExpiresOn(null);
        job.setArchiveFilename(null);
        job.setArchivePath(null);
        job.setDownloadCount(0);
        eventPublisher.publishEvent(new DownloadJobCreatedEvent(job.getId()));
        return toDto(job);
    }

    @Transactional
    public void delete(String peppolId, Long id) {
        DownloadJob job = downloadJobRepository.findByIdAndPeppolIdForUpdate(id, peppolId)
                .orElseThrow(() -> new NotFoundException("Download job does not exist"));
        deleteJobFiles(job);
        downloadJobRepository.delete(job);
    }

    @Transactional
    public Optional<ArchiveWork> claim(Long id) {
        Optional<DownloadJob> result = downloadJobRepository.findByIdForUpdate(id);
        if (result.isEmpty() || result.get().getStatus() != DownloadJob.Status.PENDING) {
            return Optional.empty();
        }
        DownloadJob job = result.get();
        job.setStatus(DownloadJob.Status.PROCESSING);
        job.setProcessingStartedOn(Instant.now());
        job.setFailureMessage(null);
        return Optional.of(new ArchiveWork(
                job.getId(),
                job.getCompany().getId(),
                job.getCompany().getPeppolId(),
                job.getFromDate(),
                job.getToDate(),
                archiveFilename(job.getFromDate(), job.getToDate())
        ));
    }

    @Transactional(readOnly = true)
    public List<ArchiveDocument> loadDocuments(ArchiveWork work) {
        DateBounds dateBounds = bounds(work.fromDate(), work.toDate());
        return documentRepository.findAllForArchive(
                        work.peppolId(), dateBounds.startInclusive(), dateBounds.endExclusive()).stream()
                .map(document -> new ArchiveDocument(
                        document.getId().toString(),
                        document.getDirection(),
                        document.getInvoiceReference(),
                        document.getUbl()
                ))
                .toList();
    }

    @Transactional
    public boolean publishReady(ArchiveWork work, Path temporaryFile) throws IOException {
        Optional<DownloadJob> result = downloadJobRepository.findByIdForUpdate(work.jobId());
        if (result.isEmpty() || result.get().getStatus() != DownloadJob.Status.PROCESSING) {
            return false;
        }

        DownloadJob job = result.get();
        Path relative = relativeArchivePath(work);
        Path finalFile = resolveStoredPath(relative.toString());
        Files.createDirectories(finalFile.getParent());
        try {
            Files.move(temporaryFile, finalFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporaryFile, finalFile, StandardCopyOption.REPLACE_EXISTING);
        }

        Instant now = Instant.now();
        job.setArchiveFilename(work.archiveFilename());
        job.setArchivePath(relative.toString());
        job.setCompletedOn(now);
        job.setExpiresOn(now.plus(RETENTION));
        job.setProcessingStartedOn(null);
        job.setFailureMessage(null);
        job.setStatus(DownloadJob.Status.READY);
        return true;
    }

    @Transactional
    public void markFailed(Long id, Exception failure) {
        downloadJobRepository.findByIdForUpdate(id).ifPresent(job -> {
            if (job.getStatus() == DownloadJob.Status.PROCESSING) {
                job.setStatus(DownloadJob.Status.FAILED);
                job.setProcessingStartedOn(null);
                job.setFailureMessage(failureMessage(failure));
            }
        });
    }

    @Transactional
    public DownloadReservation reserveDownload(String peppolId, Long id) {
        DownloadJob job = downloadJobRepository.findByIdAndPeppolIdForUpdate(id, peppolId)
                .orElseThrow(() -> new NotFoundException("Download job does not exist"));
        if (job.getStatus() != DownloadJob.Status.READY) {
            throw new ConflictException("Archive is not ready for download");
        }
        if (job.getExpiresOn() == null || !job.getExpiresOn().isAfter(Instant.now())) {
            deleteJobFiles(job);
            downloadJobRepository.delete(job);
            throw new NotFoundException("Download link has expired");
        }
        if (job.getDownloadCount() >= MAX_DOWNLOADS) {
            throw new NotFoundException("Download link is no longer available");
        }

        Path file = resolveStoredPath(job.getArchivePath());
        if (!Files.isRegularFile(file)) {
            job.setStatus(DownloadJob.Status.FAILED);
            job.setFailureMessage("The generated archive file is missing");
            throw new ConflictException("Archive file is unavailable");
        }

        job.setDownloadCount(job.getDownloadCount() + 1);
        return new DownloadReservation(job.getId(), file, job.getArchiveFilename(),
                job.getDownloadCount() == MAX_DOWNLOADS);
    }

    @Transactional
    public void completeTerminalDownload(DownloadReservation reservation) {
        if (!reservation.terminal()) {
            return;
        }
        downloadJobRepository.findByIdForUpdate(reservation.jobId()).ifPresent(job -> {
            if (job.getDownloadCount() >= MAX_DOWNLOADS) {
                deleteJobFiles(job);
                downloadJobRepository.delete(job);
            }
        });
    }

    @Transactional
    public void expireReadyJobs() {
        downloadJobRepository.findAllByStatusAndExpiresOnLessThanEqual(DownloadJob.Status.READY, Instant.now())
                .forEach(job -> {
                    deleteJobFiles(job);
                    downloadJobRepository.delete(job);
                });
    }

    @Transactional
    public List<Long> recoverAndFindPendingJobs() {
        downloadJobRepository.recoverStaleProcessing(
                Instant.now().minus(Duration.ofHours(1)),
                DownloadJob.Status.PENDING,
                DownloadJob.Status.PROCESSING
        );
        return downloadJobRepository.findAllByStatus(DownloadJob.Status.PENDING).stream()
                .map(DownloadJob::getId)
                .toList();
    }

    public Path temporaryArchivePath(ArchiveWork work) throws IOException {
        Path directory = resolveStoredPath(Path.of(work.companyId().toString(), work.jobId().toString()).toString());
        Files.createDirectories(directory);
        return Files.createTempFile(directory, "archive-", ".zip.part");
    }

    public void discardTemporaryFile(Path temporaryFile) {
        try {
            Files.deleteIfExists(temporaryFile);
            deleteDirectoryIfEmpty(temporaryFile.getParent());
        } catch (IOException ignored) {
            // A later cleanup pass or administrator can remove an inaccessible temporary file.
        }
    }

    private void validatePeriod(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both dates are required");
        }
        if (fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From date must not be after to date");
        }
        if (fromDate.getYear() != toDate.getYear()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both dates must be in the same year");
        }
    }

    private void ensureSlotAvailable(Company company) {
        if (downloadJobRepository.countByCompanyAndStatusIn(company, SLOT_STATUSES) >= MAX_ACTIVE_JOBS) {
            throw new ConflictException("At most five pending or ready archives are allowed");
        }
    }

    private void purgeExpiredForCompany(Company company) {
        downloadJobRepository.findAllByCompanyPeppolIdOrderByCreatedOnDesc(company.getPeppolId()).stream()
                .filter(job -> job.getStatus() == DownloadJob.Status.READY)
                .filter(job -> job.getExpiresOn() != null && !job.getExpiresOn().isAfter(Instant.now()))
                .forEach(job -> {
                    deleteJobFiles(job);
                    downloadJobRepository.delete(job);
                });
    }

    private void deleteJobFiles(DownloadJob job) {
        Path jobDirectory = resolveStoredPath(Path.of(job.getCompany().getId().toString(), job.getId().toString()).toString());
        if (!Files.exists(jobDirectory)) {
            return;
        }
        try (var paths = Files.walk(jobDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new ArchiveFileException(e);
                }
            });
        } catch (IOException e) {
            throw new ArchiveFileException(e);
        }
    }

    private void deleteDirectoryIfEmpty(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        try (var entries = Files.list(directory)) {
            if (entries.findAny().isEmpty()) {
                Files.deleteIfExists(directory);
            }
        }
    }

    private Path relativeArchivePath(ArchiveWork work) {
        return Path.of(work.companyId().toString(), work.jobId().toString(), work.archiveFilename());
    }

    private Path resolveStoredPath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new ArchiveFileException("Archive path is missing");
        }
        Path root = Path.of(archiveDirectory).toAbsolutePath().normalize();
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new ArchiveFileException("Archive path escapes configured directory");
        }
        return resolved;
    }

    private static DateBounds bounds(LocalDate fromDate, LocalDate toDate) {
        return new DateBounds(
                fromDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
                toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        );
    }

    private static String archiveFilename(LocalDate fromDate, LocalDate toDate) {
        return ARCHIVE_DATE.format(fromDate) + "-" + ARCHIVE_DATE.format(toDate) + ".zip";
    }

    private static String failureMessage(Exception failure) {
        String message = failure.getMessage();
        if (failure instanceof IllegalStateException && message != null
                && message.startsWith("No finalized documents")) {
            return message;
        }
        return "Archive generation failed";
    }

    private static DownloadJobDto toDto(DownloadJob job) {
        return new DownloadJobDto(
                job.getId(), job.getFromDate(), job.getToDate(), job.getStatus(),
                job.getArchiveFilename(), job.getCreatedOn(), job.getCompletedOn(),
                job.getExpiresOn(), job.getDownloadCount(), job.getFailureMessage()
        );
    }

    private record DateBounds(Instant startInclusive, Instant endExclusive) {
    }

    public record ArchiveWork(Long jobId, Long companyId, String peppolId, LocalDate fromDate,
                              LocalDate toDate, String archiveFilename) {
    }

    public record ArchiveDocument(String id, DocumentDirection direction, String invoiceReference, String ubl) {
    }

    public record DownloadReservation(Long jobId, Path file, String filename, boolean terminal) {
    }

    private static final class ArchiveFileException extends RuntimeException {
        private ArchiveFileException(Exception cause) {
            super(cause);
        }

        private ArchiveFileException(String message) {
            super(message);
        }
    }
}

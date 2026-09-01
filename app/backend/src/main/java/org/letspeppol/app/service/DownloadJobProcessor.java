package org.letspeppol.app.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.app.model.DocumentDirection;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class DownloadJobProcessor {

    private static final int MAX_REFERENCE_LENGTH = 160;

    private final DownloadJobService downloadJobService;

    public void process(Long jobId) {
        DownloadJobService.ArchiveWork work = downloadJobService.claim(jobId).orElse(null);
        if (work == null) {
            return;
        }

        Path temporaryFile = null;
        try {
            List<DownloadJobService.ArchiveDocument> documents = downloadJobService.loadDocuments(work);
            if (documents.isEmpty()) {
                throw new IllegalStateException("No finalized documents with UBL data exist in this period");
            }
            temporaryFile = downloadJobService.temporaryArchivePath(work);
            writeArchive(temporaryFile, documents);
            if (!downloadJobService.publishReady(work, temporaryFile)) {
                downloadJobService.discardTemporaryFile(temporaryFile);
            }
        } catch (Exception e) {
            if (temporaryFile != null) {
                downloadJobService.discardTemporaryFile(temporaryFile);
            }
            downloadJobService.markFailed(jobId, e);
            log.warn("Failed to generate UBL archive for download job {}: {}", jobId, e.getMessage());
        }
    }

    private void writeArchive(Path file, List<DownloadJobService.ArchiveDocument> documents) throws IOException {
        Set<String> usedNames = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file), StandardCharsets.UTF_8)) {
            for (DownloadJobService.ArchiveDocument document : documents) {
                String entryName = uniqueEntryName(document, usedNames);
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write(document.ubl().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    static String uniqueEntryName(DownloadJobService.ArchiveDocument document, Set<String> usedNames) {
        String prefix = document.direction() == DocumentDirection.INCOMING ? "in_" : "out_";
        String reference = sanitizeReference(document.invoiceReference(), document.id());
        String base = prefix + reference;
        String candidate = base + ".ubl";
        if (usedNames.add(candidate.toLowerCase(Locale.ROOT))) {
            return candidate;
        }

        String shortId = document.id().replace("-", "");
        shortId = shortId.substring(0, Math.min(8, shortId.length()));
        candidate = base + "_" + shortId + ".ubl";
        int collision = 2;
        while (!usedNames.add(candidate.toLowerCase(Locale.ROOT))) {
            candidate = base + "_" + shortId + "_" + collision++ + ".ubl";
        }
        return candidate;
    }

    private static String sanitizeReference(String invoiceReference, String fallbackId) {
        String reference = invoiceReference == null ? "" : invoiceReference.trim();
        reference = reference.replaceAll("[^A-Za-z0-9._-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[._]+", "")
                .replaceAll("[. ]+$", "");
        if (reference.isBlank()) {
            reference = fallbackId;
        }
        if (reference.length() > MAX_REFERENCE_LENGTH) {
            reference = reference.substring(0, MAX_REFERENCE_LENGTH);
        }
        return reference;
    }
}

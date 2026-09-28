package org.letspeppol.app.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.letspeppol.app.model.DocumentDirection;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DownloadJobProcessorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsArchiveWithDirectionPrefixesAndUtf8UblContent() throws Exception {
        DownloadJobService service = mock(DownloadJobService.class);
        DownloadJobProcessor processor = new DownloadJobProcessor(service);
        DownloadJobService.ArchiveWork work = new DownloadJobService.ArchiveWork(
                7L, 3L, "0208:owner", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                "20260101-20261231.zip");
        Path zipPath = temporaryDirectory.resolve("archive.zip.part");
        List<DownloadJobService.ArchiveDocument> documents = List.of(
                document("aaaaaaaa-0000-0000-0000-000000000001", DocumentDirection.INCOMING, "INV/1", "<Invoice>één</Invoice>"),
                document("bbbbbbbb-0000-0000-0000-000000000002", DocumentDirection.OUTGOING, "CN:2", "<CreditNote>deux</CreditNote>")
        );

        when(service.claim(7L)).thenReturn(java.util.Optional.of(work));
        when(service.loadDocuments(work)).thenReturn(documents);
        when(service.temporaryArchivePath(work)).thenReturn(zipPath);
        when(service.publishReady(work, zipPath)).thenReturn(true);

        processor.process(7L);

        try (ZipFile zip = new ZipFile(zipPath.toFile(), StandardCharsets.UTF_8)) {
            assertThat(zip.getEntry("in_INV_1.ubl")).isNotNull();
            assertThat(zip.getEntry("out_CN_2.ubl")).isNotNull();
            assertThat(new String(zip.getInputStream(zip.getEntry("in_INV_1.ubl")).readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("<Invoice>één</Invoice>");
        }
        verify(service).publishReady(work, zipPath);
        verify(service, never()).markFailed(anyLong(), any());
    }

    @Test
    void sanitizesUnsafeNamesAndAddsDocumentSuffixForCaseInsensitiveCollisions() {
        Set<String> usedNames = new HashSet<>();
        String first = DownloadJobProcessor.uniqueEntryName(
                document("aaaaaaaa-0000-0000-0000-000000000001", DocumentDirection.INCOMING, "../Invoice A", "ubl"), usedNames);
        String second = DownloadJobProcessor.uniqueEntryName(
                document("bbbbbbbb-0000-0000-0000-000000000002", DocumentDirection.INCOMING, "..\\invoice a", "ubl"), usedNames);

        assertThat(first).isEqualTo("in_Invoice_A.ubl");
        assertThat(second).isEqualTo("in_invoice_a_bbbbbbbb.ubl");
    }

    @Test
    void marksJobFailedAndDiscardsTemporaryOutputWhenPublicationFails() throws Exception {
        DownloadJobService service = mock(DownloadJobService.class);
        DownloadJobProcessor processor = new DownloadJobProcessor(service);
        DownloadJobService.ArchiveWork work = new DownloadJobService.ArchiveWork(
                9L, 3L, "0208:owner", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                "20260101-20260131.zip");
        Path zipPath = temporaryDirectory.resolve("failed.zip.part");

        when(service.claim(9L)).thenReturn(java.util.Optional.of(work));
        when(service.loadDocuments(work)).thenReturn(List.of(document(
                "cccccccc-0000-0000-0000-000000000003", DocumentDirection.OUTGOING, "INV-3", "<ubl/>")));
        when(service.temporaryArchivePath(work)).thenReturn(zipPath);
        when(service.publishReady(work, zipPath)).thenThrow(new java.io.IOException("disk full"));

        processor.process(9L);

        ArgumentCaptor<Exception> failure = ArgumentCaptor.forClass(Exception.class);
        verify(service).discardTemporaryFile(zipPath);
        verify(service).markFailed(eq(9L), failure.capture());
        assertThat(failure.getValue()).hasMessageContaining("disk full");
    }

    private static DownloadJobService.ArchiveDocument document(
            String id, DocumentDirection direction, String reference, String ubl) {
        return new DownloadJobService.ArchiveDocument(id, direction, reference, ubl);
    }
}

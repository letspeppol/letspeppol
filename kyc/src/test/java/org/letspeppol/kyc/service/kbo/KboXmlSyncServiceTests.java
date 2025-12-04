package org.letspeppol.kyc.service.kbo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.letspeppol.kyc.model.kbo.KboProcessedZip;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.letspeppol.kyc.repository.KboProcessedZipRepository;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KboXmlSyncServiceTests {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private KboXmlParserService kboXmlParserService;

    @Mock
    private KboSftpClient kboSftpClient;

    @Mock
    private KboProcessedZipRepository kboProcessedZipRepository;

    @InjectMocks
    private KboXmlSyncService kboXmlSyncService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // override dataDir via reflection
        try {
            var dataDirField = KboXmlSyncService.class.getDeclaredField("dataDir");
            dataDirField.setAccessible(true);
            dataDirField.set(kboXmlSyncService, tempDir.toString());

            var baseDirField = KboXmlSyncService.class.getDeclaredField("baseDir");
            baseDirField.setAccessible(true);
            baseDirField.set(kboXmlSyncService, "/remote");

            var deltaDirField = KboXmlSyncService.class.getDeclaredField("deltaDir");
            deltaDirField.setAccessible(true);
            deltaDirField.set(kboXmlSyncService, "delta");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(companyRepository.count()).thenReturn(1000L); // ensure delta sync runs
    }

    @Test
    void syncDelta_skipsAlreadyProcessedZipsAndRecordsNewOnes() throws Exception {
        when(kboSftpClient.listFiles(anyString())).thenReturn(List.of("A.ZIP", "B.ZIP"));
        when(kboProcessedZipRepository.existsByFilename("A.ZIP")).thenReturn(true);
        when(kboProcessedZipRepository.existsByFilename("B.ZIP")).thenReturn(false);

        doAnswer(invocation -> {
            Path target = invocation.getArgument(1);
            createTestZipWithXml(target);
            return null;
        }).when(kboSftpClient).downloadFile(anyString(), any(Path.class));

        doNothing().when(kboXmlParserService).importEnterprises(any(InputStream.class));

        kboXmlSyncService.syncDelta();

        verify(kboSftpClient, never()).downloadFile(contains("A.ZIP"), any(Path.class));
        verify(kboSftpClient).downloadFile(contains("B.ZIP"), any(Path.class));
        verify(kboXmlParserService).importEnterprises(any(InputStream.class));

        ArgumentCaptor<KboProcessedZip> captor = ArgumentCaptor.forClass(KboProcessedZip.class);
        verify(kboProcessedZipRepository).save(captor.capture());
        KboProcessedZip saved = captor.getValue();
        assertThat(saved).isNotNull();
        assertThat(saved.getFilename()).isEqualTo("B.ZIP");
        assertThat(saved.getProcessedAt()).isNotNull();
    }

    @Test
    void syncDelta_doesNotRecordZipOnFailureAndCleansUpFiles() throws Exception {
        when(kboSftpClient.listFiles(anyString())).thenReturn(List.of("C.ZIP"));
        when(kboProcessedZipRepository.existsByFilename("C.ZIP")).thenReturn(false);

        doAnswer(invocation -> {
            Path target = invocation.getArgument(1);
            createTestZipWithXml(target);
            return null;
        }).when(kboSftpClient).downloadFile(anyString(), any(Path.class));

        doThrow(new RuntimeException("Import failed")).when(kboXmlParserService).importEnterprises(any(InputStream.class));

        kboXmlSyncService.syncDelta();

        verify(kboProcessedZipRepository, never()).save(any());

        Path kboBase = tempDir.resolve("kbo");
        assertThat(Files.exists(kboBase)).isTrue();
        try (Stream<Path> files = Files.list(kboBase)) {
            assertThat(files.findAny()).isEmpty();
        }
    }

    private void createTestZipWithXml(Path zipPath) throws IOException {
        Files.createDirectories(zipPath.getParent());
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            ZipEntry entry = new ZipEntry("test.xml");
            zos.putNextEntry(entry);
            zos.write("<root></root>".getBytes());
            zos.closeEntry();
        }
    }
}

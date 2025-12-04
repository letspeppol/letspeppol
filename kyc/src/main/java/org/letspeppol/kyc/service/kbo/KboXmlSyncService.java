package org.letspeppol.kyc.service.kbo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.model.kbo.KboProcessedZip;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.letspeppol.kyc.repository.KboProcessedZipRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class KboXmlSyncService {

    private final CompanyRepository companyRepository;
    private final KboXmlParserService kboXmlParserService;
    private final KboSftpClient kboSftpClient;
    private final KboProcessedZipRepository kboProcessedZipRepository;

    @Value("${kyc.data.dir:/tmp}")
    private String dataDir;

    @Value("${kbo.sftp.base-dir:/}")
    private String baseDir;

    @Value("${kbo.sftp.full-dir:full}")
    private String fullDir;

    @Value("${kbo.sftp.delta-dir:delta}")
    private String deltaDir;

    private static final long INITIAL_LOAD_THRESHOLD = 1000L;

    public void initialSync() {
        long count = companyRepository.count();
        if (count >= INITIAL_LOAD_THRESHOLD) {
            log.info("Initial KBO load already done ({} companies)", count);
            return;
        }

        log.info("Starting initial KBO load; current company count: {}", count);
        String remoteFullDir = concatPath(baseDir, fullDir);
        List<String> files = kboSftpClient.listFiles(remoteFullDir);
        List<String> zips = files.stream()
                .filter(name -> name.toLowerCase().endsWith(".zip"))
                .sorted()
                .toList();

        if (zips.isEmpty()) {
            throw new KboSyncException("No ZIP files found in remote full directory: " + remoteFullDir);
        }

        // Prefer the last ZIP lexicographically
        String remoteZipName = zips.get(zips.size() - 1);
        String remoteZipPath = concatPath(remoteFullDir, remoteZipName);

        Path kboBase = Paths.get(dataDir, "kbo");
        Path localZip = kboBase.resolve(remoteZipName);

        try {
            Files.createDirectories(kboBase);
        } catch (IOException e) {
            throw new KboSyncException("Failed to create KBO data directory: " + kboBase, e);
        }

        log.info("Downloading initial full KBO ZIP: {} -> {}", remoteZipPath, localZip);
        kboSftpClient.downloadFile(remoteZipPath, localZip);

        Path localXml = extractSingleXml(localZip);

//        Path localXml = Path.of("/opt/downloads/tmp/kbo/D20251101.xml"); // For testing purposes only
        log.info("Importing initial KBO XML from {}", localXml);
        try (InputStream in = Files.newInputStream(localXml)) {
            kboXmlParserService.importEnterprises(in);
        } catch (IOException e) {
            throw new KboSyncException("Failed to read extracted XML file: " + localXml, e);
        } finally {
            //cleanupFile(localXml);
            //cleanupFile(localZip);
        }

        long after = companyRepository.count();
        log.info("Initial KBO load completed. Company count before: {}, after: {}", count, after);
    }

    //@Scheduled(cron = "${kbo.sftp.delta-cron:0 30 2 * * *}")
    public void syncDeltaDaily() {
        try {
            syncDelta();
        } catch (Exception ex) {
            log.error("Scheduled KBO delta sync failed", ex);
        }
    }

    public void syncDelta() {
        long count = companyRepository.count();
        if (count < INITIAL_LOAD_THRESHOLD) {
            log.info("Skipping delta sync because initial load is not completed ({} < {})", count, INITIAL_LOAD_THRESHOLD);
            return;
        }

        String remoteDeltaDir = concatPath(baseDir, deltaDir);
        List<String> files = kboSftpClient.listFiles(remoteDeltaDir);
        List<String> zips = files.stream()
                .filter(name -> name.toUpperCase().endsWith(".ZIP"))
                .sorted(Comparator.naturalOrder())
                .toList();

        if (zips.isEmpty()) {
            log.info("No delta ZIP files found in {}", remoteDeltaDir);
            return;
        }

        Path kboBase = Paths.get(dataDir, "kbo");
        try {
            Files.createDirectories(kboBase);
        } catch (IOException e) {
            throw new KboSyncException("Failed to create KBO data directory: " + kboBase, e);
        }

        for (String zipName : zips) {
            if (kboProcessedZipRepository.existsByFilename(zipName)) {
                log.info("Skipping already processed delta ZIP {}", zipName);
                continue;
            }

            String remoteZipPath = concatPath(remoteDeltaDir, zipName);
            Path localZip = kboBase.resolve(zipName);

            log.info("Downloading delta KBO ZIP: {} -> {}", remoteZipPath, localZip);
            kboSftpClient.downloadFile(remoteZipPath, localZip);

            try {
                Path localXml = extractSingleXml(localZip);
                log.info("Importing delta KBO XML from {}", localXml);
                try (InputStream in = Files.newInputStream(localXml)) {
                    kboXmlParserService.importEnterprises(in);
                    kboProcessedZipRepository.save(new KboProcessedZip(zipName, Instant.now()));
                } finally {
                    cleanupFile(localXml);
                }
            } catch (Exception e) {
                log.error("Failed to process delta ZIP {}", zipName, e);
            } finally {
                cleanupFile(localZip);
            }
        }
    }

    private String concatPath(String parent, String child) {
        if (parent.endsWith("/")) {
            return parent + child;
        }
        return parent + "/" + child;
    }

    private Path extractSingleXml(Path zipFile) {
        if (!Files.exists(zipFile)) {
            throw new KboSyncException("ZIP file does not exist: " + zipFile);
        }

        Path targetDir = zipFile.getParent();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            Path chosen = null;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (!entry.isDirectory()
                        && entryName.toLowerCase().endsWith(".xml")
                        && !entryName.toLowerCase().endsWith(".codes.xml")) {
                    Path out = targetDir.resolve(entryName);
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    if (chosen == null) {
                        chosen = out;
                    }
                }
                zis.closeEntry();
            }
            if (chosen == null) {
                throw new KboSyncException("No XML entry found in ZIP (excluding .codes.xml): " + zipFile);
            }
            return chosen;
        } catch (IOException e) {
            throw new KboSyncException("Failed to extract XML from ZIP: " + zipFile, e);
        }
    }

    private void cleanupFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
            log.debug("Deleted temporary KBO file {}", path);
        } catch (IOException e) {
            log.warn("Failed to delete temporary KBO file {}", path, e);
        }
    }
}

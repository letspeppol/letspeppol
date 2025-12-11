package org.letspeppol.app.service;

import lombok.RequiredArgsConstructor;
import org.letspeppol.app.model.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Transactional
@Service
public class BackupService {

    public static final String DEFAULT_CONTENT_FOR_NO_ARCHIVE = "No Archive";

    @Value("${backup.data.dir:#{null}}")
    private String dataDirectory;
    @Value("${spring.application.name}")
    private String applicationName;

    public Path backupFilePath(Document document) {
        ZonedDateTime zonedDateTime = document.getCreatedOn() == null ? ZonedDateTime.now(ZoneId.systemDefault()) : document.getCreatedOn().atZone(ZoneId.systemDefault());
        return Paths.get(
                (dataDirectory == null || dataDirectory.isBlank()) ? System.getProperty("java.io.tmpdir") : dataDirectory,
                "backup",
                applicationName,
                document.getOwnerPeppolId().replace(':', '_'),
                document.getDirection().toString(),
                String.valueOf(zonedDateTime.getYear()),
                String.valueOf(zonedDateTime.getMonthValue()),
                document.getId() + ".ubl"
        );
    }

    public void backupFile(Document document) {
        Path filePath = backupFilePath(document);
        Path parent = filePath.getParent();
        try {
            if (parent != null) {
                System.out.println("Writing backup folder to: " + parent);
                Files.createDirectories(parent);
            }
            System.out.println("Writing file as backup to: " + filePath);
            Files.writeString(filePath, document.getUbl(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void clearBackupFile(Document document) {
        Path filePath = backupFilePath(document);
        try {
            Files.writeString(filePath, DEFAULT_CONTENT_FOR_NO_ARCHIVE, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

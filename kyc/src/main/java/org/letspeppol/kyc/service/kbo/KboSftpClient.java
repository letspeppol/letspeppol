package org.letspeppol.kyc.service.kbo;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

public interface KboSftpClient {

    List<String> listFiles(String directory);

    void downloadFile(String remotePath, Path localPath);

    InputStream openFile(String remotePath);
}


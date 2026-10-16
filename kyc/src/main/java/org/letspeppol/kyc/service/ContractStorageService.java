package org.letspeppol.kyc.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class ContractStorageService {

    private final Path contractDirectory;

    public ContractStorageService(@Value("${contract.data.dir:}") String dataDirectory) throws IOException {
        String resolvedDirectory = dataDirectory == null || dataDirectory.isBlank()
                ? System.getProperty("java.io.tmpdir") : dataDirectory;
        contractDirectory = Path.of(resolvedDirectory, "/contracts");
        Files.createDirectories(contractDirectory);
    }

    public byte[] getContract(String peppolId, Long accountId) {
        try {
            return Files.readAllBytes(contractPath(peppolId, accountId));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read signed contract", e);
        }
    }

    public void storeContract(String peppolId, Long accountId, byte[] signedContract) {
        try {
            Files.write(contractPath(peppolId, accountId), signedContract);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store signed contract", e);
        }
    }

    private Path contractPath(String peppolId, Long accountId) {
        return contractDirectory.resolve("contract_%s_%d.pdf".formatted(peppolId.replace(':', '_'), accountId));
    }
}

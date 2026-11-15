package org.letspeppol.kyc.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.converter.RsaKeyConverters;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;

class LocalJwtKeyConfigTest {

    @TempDir
    Path directory;

    @Test
    void createsAKeyPairSpringCanRead() throws IOException {
        Path privateKey = directory.resolve("keys/kyc-private.pem");
        Path publicKey = directory.resolve("keys/kyc-public.pem");

        LocalJwtKeyConfig.createKeyPairIfMissing("file:" + privateKey, "file:" + publicKey);

        try (InputStream privateStream = Files.newInputStream(privateKey);
             InputStream publicStream = Files.newInputStream(publicKey)) {
            RSAPrivateKey readPrivateKey = RsaKeyConverters.pkcs8().convert(privateStream);
            RSAPublicKey readPublicKey = RsaKeyConverters.x509().convert(publicStream);
            assertThat(readPrivateKey.getModulus()).isEqualTo(readPublicKey.getModulus());
        }
    }

    @Test
    void keepsAnExistingKeyPair() throws IOException {
        Path privateKey = directory.resolve("kyc-private.pem");
        Path publicKey = directory.resolve("kyc-public.pem");
        LocalJwtKeyConfig.createKeyPairIfMissing("file:" + privateKey, "file:" + publicKey);
        String firstPrivateKey = Files.readString(privateKey);

        LocalJwtKeyConfig.createKeyPairIfMissing("file:" + privateKey, "file:" + publicKey);

        assertThat(Files.readString(privateKey)).isEqualTo(firstPrivateKey);
    }

    @Test
    void leavesClasspathLocationsAlone() {
        LocalJwtKeyConfig.createKeyPairIfMissing("classpath:kyc-private.pem", "classpath:kyc-public.pem");

        assertThat(directory).isEmptyDirectory();
    }
}

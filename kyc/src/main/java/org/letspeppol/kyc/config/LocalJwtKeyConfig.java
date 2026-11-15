package org.letspeppol.kyc.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

@Configuration
@Profile("local")
public class LocalJwtKeyConfig {

    private static final String FILE_PREFIX = "file:";

    @Bean
    static BeanFactoryPostProcessor localJwtKeyPair(Environment environment) {
        return beanFactory -> createKeyPairIfMissing(
                environment.getRequiredProperty("jwt.private-key"),
                environment.getRequiredProperty("jwt.public-key"));
    }

    static void createKeyPairIfMissing(String privateKeyLocation, String publicKeyLocation) {
        if (!privateKeyLocation.startsWith(FILE_PREFIX) || !publicKeyLocation.startsWith(FILE_PREFIX)) {
            return;
        }
        Path privateKey = Path.of(privateKeyLocation.substring(FILE_PREFIX.length()));
        Path publicKey = Path.of(publicKeyLocation.substring(FILE_PREFIX.length()));
        if (Files.exists(privateKey) && Files.exists(publicKey)) {
            return;
        }
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            Files.createDirectories(privateKey.toAbsolutePath().getParent());
            Files.createDirectories(publicKey.toAbsolutePath().getParent());
            Files.writeString(privateKey, pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()));
            Files.writeString(publicKey, pem("PUBLIC KEY", keyPair.getPublic().getEncoded()));
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Could not create the local JWT signing key pair", e);
        }
    }

    private static String pem(String type, byte[] der) {
        Base64.Encoder encoder = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII));
        return "-----BEGIN " + type + "-----\n" + encoder.encodeToString(der) + "\n-----END " + type + "-----\n";
    }
}

package org.letspeppol.kyc.service;

import jakarta.annotation.PostConstruct;
import org.letspeppol.kyc.config.EncryptionProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class EncryptionService {
    private static final String ALGO = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128; // 16 bytes tag
    private static final int IV_BYTES = 12; // Recommended 96-bit IV for GCM
    private static final char DELIM = ':'; // Separator between keyId and base64 blob

    private final EncryptionProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, SecretKey> secretKeys = new HashMap<>();

    public EncryptionService(EncryptionProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        properties.getKeys().forEach((id, base64) -> {
            byte[] keyBytes = Base64.getDecoder().decode(base64.trim());
            if (!isValidLength(keyBytes.length)) {
                throw new IllegalArgumentException("Key '" + id + "' invalid length: " + keyBytes.length);
            }
            secretKeys.put(id, new SecretKeySpec(keyBytes, ALGO));
        });
        if (secretKeys.isEmpty()) {
            throw new IllegalStateException("No encryption keys configured");
        }
    }

    private boolean isValidLength(int len) {
        return len == 16 || len == 24 || len == 32;
    }

    /**
     * Encrypt plain text using AES/GCM.
     * Output format: keyId:Base64( IV || CIPHERTEXT||TAG )
     */
    public String encrypt(String plainText) {
        if (plainText == null) return null;
        String keyId = properties.getActiveKeyId();
        SecretKey key = secretKeys.get(keyId);
        if (key == null) {
            throw new IllegalStateException("Active key id '" + keyId + "' not loaded");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv).put(cipherText);
            String body = Base64.getEncoder().encodeToString(buffer.array());
            return keyId + DELIM + body;
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * Decrypt ciphertext in the enforced format keyId:Base64( IV || CIPHERTEXT||TAG ).
     * Throws IllegalStateException if format invalid or key unknown.
     */
    public String decrypt(String cipherText) {
        if (cipherText == null) return null;
        int idx = cipherText.indexOf(DELIM);
        if (idx <= 0) {
            throw new IllegalStateException("Ciphertext missing key id prefix");
        }
        String keyId = cipherText.substring(0, idx);
        SecretKey key = secretKeys.get(keyId);
        if (key == null) {
            throw new IllegalStateException("Unknown encryption key id: " + keyId);
        }
        String base64Part = cipherText.substring(idx + 1);
        return decryptWithKey(base64Part, key);
    }

    private String decryptWithKey(String base64, SecretKey key) {
        try {
            byte[] allBytes = Base64.getDecoder().decode(base64);
            if (allBytes.length < IV_BYTES + 1) {
                throw new IllegalArgumentException("Cipher text too short");
            }
            byte[] iv = Arrays.copyOfRange(allBytes, 0, IV_BYTES);
            byte[] cipherBytes = Arrays.copyOfRange(allBytes, IV_BYTES, allBytes.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(cipherBytes);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}

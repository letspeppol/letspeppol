package org.letspeppol.kyc.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.config.EncryptionProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class EncryptionServiceTests {

    @Autowired
    EncryptionService encryptionService;

    @Autowired
    EncryptionProperties encryptionProperties;

    @Test
    @DisplayName("Encrypt then decrypt returns original with keyId prefix")
    void roundTripWithPrefix() {
        String original = "Sensitive Data 123 !";
        String encrypted = encryptionService.encrypt(original);
        assertTrue(encrypted.contains(":"), "Ciphertext should contain ':' delimiter");
        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(original, decrypted);
    }

    @Test
    @DisplayName("Decrypt should fail for tampered ciphertext (prefixed)")
    void tamperDetection() {
        String original = "Another secret";
        String encrypted = encryptionService.encrypt(original);
        char[] chars = encrypted.toCharArray();
        for (int i = chars.length - 1; i >= 0; i--) { // modify near end to affect tag/body
            if (Character.isLetterOrDigit(chars[i])) {
                chars[i] = chars[i] == 'A' ? 'B' : 'A';
                break;
            }
        }
        String tampered = new String(chars);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> encryptionService.decrypt(tampered));
        assertTrue(ex.getMessage().contains("Decryption failed"));
    }

    @Test
    @DisplayName("Rotation: ciphertext from old key decrypts after active key changes")
    void rotationOldCiphertextStillDecrypts() throws Exception {
        // Active key might be changed; simulate old key usage by encrypting with a non-active key id directly
        String active = encryptionProperties.getActiveKeyId();
        String oldKeyId = encryptionProperties.getKeys().keySet().stream().filter(k -> !k.equals(active)).findFirst().orElse(active);
        String oldKey = encryptionProperties.getKeys().get(oldKeyId);
        String original = "Data before rotation";
        String oldFormat = encryptWithKeyIdPrefix(oldKeyId, oldKey, original);
        assertEquals(original, encryptionService.decrypt(oldFormat));
    }

    @Test
    void nullHandling() {
        assertNull(encryptionService.encrypt(null));
        assertNull(encryptionService.decrypt(null));
    }

    // Helper to simulate prefixed format for given key id
    private String encryptWithKeyIdPrefix(String keyId, String base64Key, String plaintext) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        SecretKey key = new SecretKeySpec(keyBytes, "AES");
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
        byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
        buffer.put(iv).put(cipherText);
        return keyId + ":" + Base64.getEncoder().encodeToString(buffer.array());
    }
}

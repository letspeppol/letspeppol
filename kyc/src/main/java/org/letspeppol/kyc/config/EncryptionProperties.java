package org.letspeppol.kyc.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "encryption")
public class EncryptionProperties {
    private String activeKeyId = "default";
    private Map<String,String> keys = new HashMap<>();

    @PostConstruct
    void validate() {
        if (keys.isEmpty()) {
            throw new IllegalStateException("No encryption.keys.* configured");
        }
        // Fail fast on blank values: an unset env var (e.g. ENCRYPTION_KEY_1) binds to an
        // empty string rather than leaving the map empty, so guard against it explicitly.
        keys.forEach((id, value) -> {
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(
                        "Encryption key '" + id + "' is blank. Set the corresponding environment variable "
                                + "(e.g. ENCRYPTION_KEY_1) to a base64-encoded AES key.");
            }
        });
        if (!keys.containsKey(activeKeyId)) {
            if (keys.size() == 1) {
                // Promote the single configured key as active
                activeKeyId = keys.keySet().iterator().next();
            } else {
                throw new IllegalStateException("Active key id '" + activeKeyId + "' not present among configured keys");
            }
        }
    }
}

package org.letspeppol.kyc.config;

import jakarta.mail.internet.MimeMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Test configuration providing a no-op JavaMailSender so that beans depending
 * on mail sending (such as ActivationService) can be constructed in tests
 * without requiring a real mail infrastructure.
 */
@Configuration
@Profile("test")
public class TestMailConfig {

    @Bean
    public JavaMailSender testJavaMailSender() {
        return new JavaMailSender() {
            @Override
            public MimeMessage createMimeMessage() {
                return new jakarta.mail.internet.MimeMessage((jakarta.mail.Session) null);
            }

            @Override
            public MimeMessage createMimeMessage(java.io.InputStream contentStream) {
                try {
                    return new jakarta.mail.internet.MimeMessage(null, contentStream);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void send(MimeMessage mimeMessage) {
                // no-op for tests
            }

            @Override
            public void send(MimeMessage... mimeMessages) {
                // no-op for tests
            }

            @Override
            public void send(SimpleMailMessage simpleMessage) {
                // no-op for tests
            }

            @Override
            public void send(SimpleMailMessage... simpleMessages) {
                // no-op for tests
            }
        };
    }
}


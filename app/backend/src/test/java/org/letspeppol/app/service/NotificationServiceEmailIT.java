package org.letspeppol.app.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.letspeppol.app.PostgresIntegrationTest;
import org.letspeppol.app.model.*;
import org.letspeppol.app.repository.EmailJobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Manual integration test that sends a real notification email using Postgres testcontainer.
 *
 * Set these environment variables before running:
 *   MAIL_HOST, MAIL_PORT, MAIL_USER, MAIL_PASS, MAIL_FROM, TEST_RECIPIENT
 *   DOCKER_API_VERSION=1.41  (if your Docker client/server versions mismatch)
 */
@Disabled("Manual test — remove @Disabled and set SMTP env vars to run")
@SpringBootTest
@TestPropertySource(properties = {
        "email.jobs.poll-delay=PT999H",
        "proxy.synchronize.delay-ms=999999999",
})
class NotificationServiceEmailIT extends PostgresIntegrationTest {

    private static final String TO_ADDRESS = env("TEST_RECIPIENT", "test@example.com");

    @DynamicPropertySource
    static void registerMailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> env("MAIL_HOST", "localhost"));
        registry.add("spring.mail.port", () -> env("MAIL_PORT", "465"));
        registry.add("spring.mail.username", () -> env("MAIL_USER", ""));
        registry.add("spring.mail.password", () -> env("MAIL_PASS", ""));
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "true");
        registry.add("spring.mail.properties.mail.smtp.ssl.enable", () -> "true");
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.connectiontimeout", () -> "10000");
        registry.add("spring.mail.properties.mail.smtp.timeout", () -> "10000");
        registry.add("spring.mail.properties.mail.smtp.writetimeout", () -> "10000");
        registry.add("notification.mail.from", () -> env("MAIL_FROM", "test@example.com"));
        // Sponsors
        registry.add("sponsors.email-text", () -> "We are supported by");
        registry.add("sponsors.base-url", () -> "https://letspeppol.org/img/supporters/");
        registry.add("sponsors.list[0].name", () -> "Brightest");
        registry.add("sponsors.list[0].logo", () -> "brightest.svg");
        registry.add("sponsors.list[0].url", () -> "https://brightest.be");
    }

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private EmailJobRepository emailJobRepository;

    @Test
    void sendNotificationEmail() {
        Company company = new Company(
                "0208:BE0123456789",
                "BE0123456789",
                "Test Company NV",
                "Test User",
                TO_ADDRESS,
                "Brussels", "1000", "Rue de la Loi 1", "BE"
        );
        company.setEnableEmailNotification(true);
        company.setAddAttachmentToNotification(false);

        Document document = new Document(
                UUID.randomUUID(),
                DocumentDirection.INCOMING,
                company.getPeppolId(),
                "0208:BE9876543210",
                Instant.now(),
                null,
                Instant.now(),
                "SUCCESS",
                null,
                null,
                null,
                null,
                "Supplier NV",
                "INV-2026-0042",
                null,
                null,
                DocumentType.INVOICE,
                Currency.getInstance("EUR"),
                new BigDecimal("1234.56"),
                Instant.now(),
                Instant.now(),
                null,
                true
        );

        notificationService.notifyIncomingDocument(company, document);

        List<EmailJob> pendingJobs = emailJobRepository.findAllByStatusOrderByCreatedOnAsc(EmailJob.Status.PENDING);
        assertFalse(pendingJobs.isEmpty(), "Expected at least one PENDING EmailJob — notifyIncomingDocument failed silently");
        System.out.println("✅ EmailJob created with id=" + pendingJobs.get(0).getId());
        System.out.println("   payload: " + pendingJobs.get(0).getPayload());

        await().atMost(30, SECONDS)
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    emailService.processEmailJobs();
                    assertTrue(emailJobRepository.findAllByStatusOrderByCreatedOnAsc(EmailJob.Status.FAILED).isEmpty(),
                            "EmailJob FAILED — check logs for SMTP errors");
                    assertFalse(emailJobRepository.findAllByStatusOrderByCreatedOnAsc(EmailJob.Status.SENT).isEmpty(),
                            "Expected at least one SENT EmailJob");
                });
        System.out.println("✅ Email sent to " + TO_ADDRESS);
    }

    private static String env(String key, String defaultValue) {
        String val = System.getenv(key);
        if (val == null || val.isBlank()) {
            val = System.getProperty(key);
        }
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }
}

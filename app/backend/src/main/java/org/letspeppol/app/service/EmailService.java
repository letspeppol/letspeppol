package org.letspeppol.app.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.app.dto.DocumentNotificationEmailDto;
import org.letspeppol.app.model.EmailJob;
import org.letspeppol.app.repository.EmailJobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Slf4j
public class EmailService {

    private final EmailJobRepository emailJobRepository;
    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;
    private final Duration minDelayBetweenEmails;

    private volatile long lastSentNanos = 0L;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public EmailService(EmailJobRepository emailJobRepository,
                        JavaMailSender mailSender,
                        ObjectMapper objectMapper,
                        @Value("${email.jobs.rate-limit:PT1S}") Duration minDelayBetweenEmails) {
        this.emailJobRepository = emailJobRepository;
        this.mailSender = mailSender;
        this.objectMapper = objectMapper;
        this.minDelayBetweenEmails = minDelayBetweenEmails;
    }

    @Scheduled(fixedDelayString = "${email.jobs.poll-delay:PT5S}")
    public void processEmailJobs() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            List<EmailJob> jobs = emailJobRepository.findAllByStatusOrderByCreatedOnAsc(EmailJob.Status.PENDING);
            if (jobs.isEmpty()) return;

            for (EmailJob job : jobs) {
                try {
                    rateLimit();
                    send(job);
                    job.setStatus(EmailJob.Status.SENT);
                    job.setSentAt(Instant.now());
                    emailJobRepository.save(job);
                } catch (Exception e) {
                    job.setStatus(EmailJob.Status.FAILED);
                    emailJobRepository.save(job);
                    log.warn("Failed to send email job {}: {}", job.getId(), e.getMessage());
                }
            }
        } finally {
            running.set(false);
        }
    }

    private void send(EmailJob job) throws Exception {
        if (job.getPayload() == null || job.getPayload().isBlank()) {
            throw new IllegalArgumentException("Email job payload is empty");
        }

        DocumentNotificationEmailDto dto = objectMapper.readValue(job.getPayload(), DocumentNotificationEmailDto.class);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());

        if (dto.from() != null && !dto.from().isBlank()) {
            helper.setFrom(dto.from());
        }

        helper.setTo(dto.to());
        setOptionalRecipients(helper, dto.cc(), true);
        setOptionalRecipients(helper, dto.bcc(), false);

        if (dto.replyTo() != null && !dto.replyTo().isBlank()) {
            helper.setReplyTo(dto.replyTo());
        }

        helper.setSubject(dto.subject() == null ? "" : dto.subject());

        String html = (dto.html() != null && !dto.html().isBlank()) ? dto.html() : null;
        String text = (dto.text() != null && !dto.text().isBlank()) ? dto.text() : "";

        if (html != null) {
            helper.setText(text, html);
        } else {
            helper.setText(text, false);
        }

        mailSender.send(message);
    }

    private void setOptionalRecipients(MimeMessageHelper helper, String recipients, boolean cc) throws Exception {
        if (recipients == null || recipients.isBlank()) return;

        String[] parts = Arrays.stream(recipients.split("[;,]"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);

        if (parts.length == 0) return;

        if (cc) {
            helper.setCc(parts);
        } else {
            helper.setBcc(parts);
        }
    }

    private synchronized void rateLimit() {
        if (minDelayBetweenEmails == null || minDelayBetweenEmails.isZero() || minDelayBetweenEmails.isNegative()) {
            return;
        }

        long minNanos = minDelayBetweenEmails.toNanos();
        long now = System.nanoTime();

        if (lastSentNanos != 0L) {
            long elapsed = now - lastSentNanos;
            long remaining = minNanos - elapsed;
            if (remaining > 0) {
                try {
                    Thread.sleep(Duration.ofNanos(remaining).toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        lastSentNanos = System.nanoTime();
    }
}

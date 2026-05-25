package org.letspeppol.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.letspeppol.app.dto.DocumentNotificationEmailDto;
import org.letspeppol.app.dto.OpenCollectiveAccountDto;
import org.letspeppol.app.dto.SponsorContributionDto;
import org.letspeppol.app.dto.SponsorInvoiceResponse;
import org.letspeppol.app.dto.SponsorInvoiceRequest;
import org.letspeppol.app.dto.UblDocumentDto;
import org.letspeppol.app.exception.ConflictException;
import org.letspeppol.app.events.EmailJobCreatedEvent;
import org.letspeppol.app.exception.NotFoundException;
import org.letspeppol.app.model.Address;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.DocumentDirection;
import org.letspeppol.app.model.DocumentType;
import org.letspeppol.app.model.EmailJob;
import org.letspeppol.app.model.SponsorInvoice;
import org.letspeppol.app.repository.CompanyRepository;
import org.letspeppol.app.repository.EmailJobRepository;
import org.letspeppol.app.repository.SponsorInvoiceRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
@Slf4j
public class SponsorInvoiceService {

    private static final String ACTING_USER_AUTHORIZATION_HEADER = "X-Acting-User-Authorization";
    private static final String TEMPLATE = "sponsors/sponsor-invoice-template.xml";
    private static final String SPONSOR_PEPPOL_ID = "0208:1029545627";
    private static final BigDecimal MAX_SELF_SERVICE_AMOUNT = new BigDecimal("2500.00");
    private static final ZoneId ZONE = ZoneId.of("Europe/Brussels");

    private final CompanyRepository companyRepository;
    private final SponsorInvoiceRepository sponsorInvoiceRepository;
    private final DonationService donationService;
    private final EmailJobRepository emailJobRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final JwtService jwtService;
    @Qualifier("proxyWebClient")
    private final WebClient proxyWebClient;
    @Value("${notification.mail.from}")
    private String notificationMailFrom;
    @Value("${sponsors.package-request.mail-to:partnership@letspeppol.org}")
    private String sponsorPackageRequestMailTo;

    public List<SponsorContributionDto> getSponsorContributions() {
        List<SponsorContributionDto> contributions = new ArrayList<>();
        try {
            contributions.addAll(sponsorInvoiceRepository.findAllByActiveTrueOrderBySponsoredOnDesc().stream()
                    .map(sponsorInvoice -> new SponsorContributionDto(
                            sponsorInvoice.getName(),
                            sponsorInvoice.getMessage(),
                            sponsorInvoice.getAmount(),
                            sponsorInvoice.getCurrency().getCurrencyCode(),
                            sponsorInvoice.getSponsoredOn()
                    ))
                    .toList());
        } catch (Exception e) {
            log.warn("Could not add Peppol sponsor contributions", e);
        }

        try {
            var donationStats = donationService.getDonationStats();
            if (donationStats == null) {
                return contributions;
            }
            List<OpenCollectiveAccountDto.Transaction> transactions = donationStats.getTransactions();
            if (transactions != null) {
                contributions.addAll(transactions.stream()
                        .filter(transaction -> transaction.getAmount() != null && transaction.getAmount().getValue() != null)
                        .map(transaction -> new SponsorContributionDto(
                                openCollectiveName(transaction),
                                "OpenCollective contribution",
                                transaction.getAmount().getValue(),
                                transaction.getAmount().getCurrency(),
                                openCollectiveDate(transaction)
                        ))
                        .toList());
            }
        } catch (Exception e) {
            log.warn("Could not add OpenCollective sponsor contributions", e);
        }

        return contributions.stream()
                .sorted(Comparator.comparing(
                        SponsorContributionDto::date,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    @Transactional
    public SponsorInvoiceResponse createSponsorInvoice(String customerPeppolId, SponsorInvoiceRequest request, String actingUserToken) {
        Company customer = companyRepository.findByPeppolId(customerPeppolId)
                .orElseThrow(() -> new NotFoundException("Company does not exist"));
        if (sponsorInvoiceRepository.existsByCompanyIdAndActiveTrueAndSponsoredOnAfter(customer.getId(), Instant.now().minus(Duration.ofHours(48)))) {
            throw new ConflictException("Sponsor invoice already created within 48 hours");
        }
        int scale = Math.max(0, request.currency().getDefaultFractionDigits());
        BigDecimal amount = request.amount().setScale(scale, RoundingMode.HALF_UP);
        if (amount.compareTo(MAX_SELF_SERVICE_AMOUNT) > 0) {
            sendSponsorPackageRequest(customer, request, amount);
            return new SponsorInvoiceResponse(
                    "PACKAGE_REQUESTED",
                    "We value your contribution and will contact you to agree on a fitting sponsor package.",
                    null
            );
        }
        SponsorInvoice sponsorInvoice = createSponsorInvoiceRecord(customer, request);
        String ublXml = buildInvoiceXml(customer, sponsorInvoice);

        UblDocumentDto payload = new UblDocumentDto(
                UUID.randomUUID(),
                DocumentDirection.OUTGOING,
                DocumentType.INVOICE,
                SPONSOR_PEPPOL_ID,
                customerPeppolId,
                Instant.now(),
                null,
                null,
                null,
                ublXml
        );

        UblDocumentDto document = proxyWebClient.post()
                .uri("/sapi/document")
                .headers(headers -> {
                    headers.setBearerAuth(jwtService.getAppTokenFromKyc());
                    headers.add(ACTING_USER_AUTHORIZATION_HEADER, "Bearer " + actingUserToken);
                })
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(UblDocumentDto.class)
                .blockOptional()
                .orElseThrow(() -> new IllegalStateException("Could not deliver sponsor invoice at PROXY"));
        return new SponsorInvoiceResponse(
                "INVOICE_CREATED",
                "Your sponsor invoice has been created and will arrive through Peppol.",
                document
        );
    }

    private SponsorInvoice createSponsorInvoiceRecord(Company customer, SponsorInvoiceRequest request) {
        int scale = Math.max(0, request.currency().getDefaultFractionDigits());
        if (request.amount().scale() > scale) {
            throw new IllegalArgumentException("Amount can have at most %d decimals".formatted(scale));
        }
        BigDecimal amount = request.amount().setScale(scale, RoundingMode.HALF_UP);
        String invoiceId = nextInvoiceId();
        SponsorInvoice sponsorInvoice = new SponsorInvoice(
                customer,
                Instant.now(),
                amount,
                request.currency(),
                request.name(),
                request.message(),
                invoiceId
        );
        return sponsorInvoiceRepository.saveAndFlush(sponsorInvoice);
    }

    private void sendSponsorPackageRequest(Company company, SponsorInvoiceRequest request, BigDecimal amount) {
        String text = """
                A company requested a sponsor package for Let’s Peppol.

                Requested contribution excluding VAT: %s %s
                Sponsor page name: %s
                Sponsor message: %s

                Company:
                Name: %s
                Display name: %s
                Peppol ID: %s
                VAT number: %s
                Subscriber: %s
                Subscriber email: %s
                """.formatted(
                amount.toPlainString(),
                request.currency().getCurrencyCode(),
                request.name(),
                request.message(),
                company.getName(),
                company.getDisplayName(),
                company.getPeppolId(),
                company.getVatNumber(),
                company.getSubscriber(),
                company.getSubscriberEmail()
        );
        DocumentNotificationEmailDto emailDto = new DocumentNotificationEmailDto(
                notificationMailFrom,
                sponsorPackageRequestMailTo,
                null,
                null,
                company.getSubscriberEmail(),
                "Sponsor package request from %s".formatted(company.getName()),
                text,
                null,
                null
        );
        try {
            EmailJob saved = emailJobRepository.save(EmailJob.builder()
                    .toAddress(sponsorPackageRequestMailTo)
                    .payload(objectMapper.writeValueAsString(emailDto))
                    .build());
            eventPublisher.publishEvent(new EmailJobCreatedEvent(saved.getId()));
        } catch (JsonProcessingException e) {
            log.error("Failed to create sponsor package request email", e);
            throw new IllegalStateException("Could not create sponsor package request email", e);
        }
    }

    private String buildInvoiceXml(Company customer, SponsorInvoice sponsorInvoice) {
        LocalDate issueDate = LocalDate.ofInstant(sponsorInvoice.getSponsoredOn(), ZONE);
        String currency = sponsorInvoice.getCurrency().getCurrencyCode();
        int scale = Math.max(0, sponsorInvoice.getCurrency().getDefaultFractionDigits());
        BigDecimal amount = sponsorInvoice.getAmount().setScale(scale, RoundingMode.HALF_UP);
        BigDecimal tax = amount.multiply(new BigDecimal("0.21")).setScale(scale, RoundingMode.HALF_UP);
        BigDecimal total = amount.add(tax).setScale(scale, RoundingMode.HALF_UP);
        String[] customerPeppol = splitPeppolId(customer.getPeppolId());
        Address address = customer.getRegisteredOffice();

        Map<String, String> values = Map.ofEntries(
                Map.entry("invoiceId", sponsorInvoice.getInvoiceId()),
                Map.entry("issueDate", issueDate.toString()),
                Map.entry("dueDate", issueDate.plusDays(30).toString()),
                Map.entry("currency", currency),
                Map.entry("amount", amount.toPlainString()),
                Map.entry("taxAmount", tax.toPlainString()),
                Map.entry("totalAmount", total.toPlainString()),
                Map.entry("customerScheme", xml(customerPeppol[0])),
                Map.entry("customerEndpoint", xml(customerPeppol[1])),
                Map.entry("customerName", xml(customer.getName())),
                Map.entry("customerStreet", xml(address != null ? address.getStreet() : "")),
                Map.entry("customerCity", xml(address != null ? address.getCity() : "")),
                Map.entry("customerPostalCode", xml(address != null ? address.getPostalCode() : "")),
                Map.entry("customerCountryCode", xml(address != null ? address.getCountryCode() : "BE")),
                Map.entry("customerVatNumber", xml(vatNumber(customer))),
                Map.entry("description", xml("name: " + sponsorInvoice.getName() + "\nmessage: " + sponsorInvoice.getMessage()))
        );

        String xml = template();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            xml = xml.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return xml;
    }

    private String template() {
        try {
            return StreamUtils.copyToString(new ClassPathResource(TEMPLATE).getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read sponsor invoice template", e);
        }
    }

    private synchronized String nextInvoiceId() {
        return sponsorInvoiceRepository.findTopByOrderByIdDesc()
                .map(SponsorInvoice::getInvoiceId)
                .map(this::incrementInvoiceId)
                .orElseGet(() -> "IN_" + LocalDate.now(ZONE).getYear() + "_0001");
    }

    private String incrementInvoiceId(String invoiceId) {
        int end = invoiceId.length() - 1;
        while (end >= 0 && !Character.isDigit(invoiceId.charAt(end))) {
            end--;
        }
        if (end < 0) {
            return "IN_" + LocalDate.now(ZONE).getYear() + "_0001";
        }

        int start = end;
        while (start >= 0 && Character.isDigit(invoiceId.charAt(start))) {
            start--;
        }
        start++;

        String number = invoiceId.substring(start, end + 1);
        String nextNumber = String.format("%0" + number.length() + "d", Integer.parseInt(number) + 1);
        return invoiceId.substring(0, start) + nextNumber + invoiceId.substring(end + 1);
    }

    private String[] splitPeppolId(String peppolId) {
        String[] parts = peppolId.split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException("Invalid Peppol ID: " + peppolId);
        }
        return parts;
    }

    private String vatNumber(Company customer) {
        if (customer.getVatNumber() != null && !customer.getVatNumber().isBlank()) {
            return customer.getVatNumber();
        }
        String[] parts = splitPeppolId(customer.getPeppolId());
        return "0208".equals(parts[0]) ? "BE" + parts[1] : parts[1];
    }

    private String xml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private String openCollectiveName(OpenCollectiveAccountDto.Transaction transaction) {
        if (transaction.getFromAccount() == null || transaction.getFromAccount().getName() == null || transaction.getFromAccount().getName().isBlank()) {
            return "OpenCollective supporter";
        }
        return transaction.getFromAccount().getName();
    }

    private Instant openCollectiveDate(OpenCollectiveAccountDto.Transaction transaction) {
        if (transaction.getCreatedAt() == null || transaction.getCreatedAt().isBlank()) {
            return null;
        }
        return Instant.parse(transaction.getCreatedAt());
    }
}

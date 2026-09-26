package org.letspeppol.app.service;

import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.app.dto.AccountInfo;
import org.letspeppol.app.dto.CompanyDto;
import org.letspeppol.app.dto.LinkedInfo;
import org.letspeppol.app.dto.ServiceRequest;
import org.letspeppol.app.exception.AppErrorCodes;
import org.letspeppol.app.exception.AppException;
import org.letspeppol.app.exception.NotFoundException;
import org.letspeppol.app.mapper.CompanyMapper;
import org.letspeppol.app.model.Company;
import org.letspeppol.app.model.VatRuleset;
import org.letspeppol.app.repository.CompanyRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompanyService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final CompanyRepository companyRepository;
    @Qualifier("kycWebClient")
    private final WebClient kycWebClient;
    private final Counter companyCreateCounter;
    private @Value("${kyc.auth.app.external-id}") String appExternalId;

    public Company add(AccountInfo request) {
        companyCreateCounter.increment();
        Company account = new Company(
                request.peppolId(),
                request.identifier(),
                request.vatNumber(),
                request.companyName(),
                request.directorName(),
                request.directorEmail(),
                request.city(),
                request.postalCode(),
                request.street(),
                "BE"
        );
        return companyRepository.save(account);
    }

    public CompanyDto get(String peppolId, String tokenValue, boolean isPeppolActive) {
        Optional<Company> optionalCompany = companyRepository.findByPeppolId(peppolId);
        if (optionalCompany.isPresent()) {
            return CompanyMapper.toDto(optionalCompany.get(), isPeppolActive);
        }
        try {
            AccountInfo accountInfo = kycWebClient.get()
                    .uri("/sapi/company")
                    .headers(headers -> headers.setBearerAuth(tokenValue))
                    .retrieve()
                    .bodyToMono(AccountInfo.class)
                    .blockOptional()
                    .orElseThrow(() -> new IllegalStateException("Account was not know at KYC"));

            return CompanyMapper.toDto(add(accountInfo), isPeppolActive);
        } catch (Exception ex) {
            log.error("Call to KYC /sapi/company failed", ex);
            throw new AppException(AppErrorCodes.KYC_REST_ERROR);
        }
    }

    public CompanyDto update(CompanyDto companyDto, boolean isPeppolActive, String tokenValue) {
        Company company = companyRepository.findByPeppolId(companyDto.peppolId()).orElseThrow(() -> new NotFoundException("Company does not exist"));
        if (company.isEnableEmailNotification() != companyDto.enableEmailNotification()) {
            ServiceRequest serviceRequest = new ServiceRequest(UUID.fromString(appExternalId));
            if (companyDto.enableEmailNotification()) {
                kycWebClient.post()
                        .uri("/sapi/linked/register")
                        .headers(headers -> headers.setBearerAuth(tokenValue))
                        .bodyValue(serviceRequest)
                        .retrieve()
                        .bodyToMono(LinkedInfo.class)
                        .blockOptional()
                        .orElseThrow(() -> new IllegalStateException("Error linking App for company " + companyDto.peppolId()));
            } else {
                kycWebClient.post()
                        .uri("/sapi/linked/unregister")
                        .headers(headers -> headers.setBearerAuth(tokenValue))
                        .bodyValue(serviceRequest)
                        .retrieve()
                        .toBodilessEntity()
                        .block();
            }
        }
        if (!company.getName().equals(companyDto.displayName()) && StringUtils.hasText(companyDto.displayName())) {
            company.setDisplayName(companyDto.displayName());
        } else {
            company.setDisplayName(null);
        }
        company.setPaymentAccountName(companyDto.paymentAccountName());
        company.setPaymentTerms(companyDto.paymentTerms());
        company.setIban(companyDto.iban());
        company.setBic(companyDto.bic());
        company.setVatRuleset(companyDto.vatRuleset() == null ? VatRuleset.VAT_REGISTERED : companyDto.vatRuleset());
        company.setEnableEmailNotification(companyDto.enableEmailNotification());
        company.setAddAttachmentToNotification(companyDto.addAttachmentToNotification());
        company.setEmailNotificationCcListIncoming(sanitizeCcList(companyDto.emailNotificationCcListIncoming()));
        company.setEmailNotificationCcListOutgoing(sanitizeCcList(companyDto.emailNotificationCcListOutgoing()));
        company.setAddPdfToSendingInvoice(companyDto.addPdfToSendingInvoice());
        // TODO        company.setNoArchive(companyDto.noArchive());
        company.getRegisteredOffice().setCity(companyDto.registeredOffice().city());
        company.getRegisteredOffice().setPostalCode(companyDto.registeredOffice().postalCode());
        company.getRegisteredOffice().setStreet(companyDto.registeredOffice().street());
        company = companyRepository.save(company);
        return CompanyMapper.toDto(company, isPeppolActive);
    }

    /**
     * Sanitizes the comma/semicolon-separated CC list stored for a company: strips control
     * characters (CR/LF) to prevent later mail-header injection, validates each entry looks
     * like an email address, and drops invalid ones. Returns null when nothing valid remains.
     */
    private String sanitizeCcList(String ccList) {
        if (ccList == null || ccList.isBlank()) {
            return null;
        }
        String cleaned = Arrays.stream(ccList.split("[;,]"))
                .map(s -> s.replaceAll("\\p{Cntrl}", "").trim())
                .filter(s -> !s.isBlank())
                .filter(s -> EMAIL_PATTERN.matcher(s).matches())
                .collect(Collectors.joining(","));
        return cleaned.isBlank() ? null : cleaned;
    }
}

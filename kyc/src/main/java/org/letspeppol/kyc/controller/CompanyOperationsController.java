package org.letspeppol.kyc.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.RegistrationRequest;
import org.letspeppol.kyc.exception.ForbiddenException;
import org.letspeppol.kyc.exception.KycErrorCodes;
import org.letspeppol.kyc.exception.KycException;
import org.letspeppol.kyc.model.AccountType;
import org.letspeppol.kyc.repository.CompanyRepository;
import org.letspeppol.kyc.repository.OwnershipRepository;
import org.letspeppol.kyc.service.ContractStorageService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/lapi/company")
public class CompanyOperationsController {

    private final CompanyRepository companyRepository;
    private final OwnershipRepository ownershipRepository;
    private final ContractStorageService contractStorageService;
    @Qualifier("ProxyWebClient")
    private final WebClient proxyWebClient;
    private final ObjectMapper objectMapper;

    @PostMapping("/{peppolId}/move")
    public ResponseEntity<String> move(@PathVariable String peppolId,
                                       @RequestParam(required = false) String accessPoint) {
        var company = companyRepository.findByPeppolId(peppolId)
                .orElseThrow(() -> new KycException(KycErrorCodes.COMPANY_NOT_FOUND));
        if (company.isSuspended()) {
            throw new ForbiddenException(KycErrorCodes.PROXY_REGISTRATION_SUSPENDED);
        }
        var admin = ownershipRepository.findFirstByCompanyPeppolIdAndTypeOrderByLastUsedDesc(peppolId, AccountType.ADMIN)
                .orElseThrow(() -> new KycException(KycErrorCodes.NO_OWNERSHIP));
        byte[] contract = contractStorageService.getContract(peppolId, admin.getAccount().getId());
        var data = new RegistrationRequest(
                company.getName(), "EN", "BE", company.getStreet(), company.getPostalCode(),
                company.getCity(), company.getVatNumber(), contract);
        var response = proxyWebClient.post()
                .uri(builder -> builder.path("/sapi/registry/{peppolId}/move")
                        .queryParamIfPresent("accessPoint", Optional.ofNullable(accessPoint)).build(peppolId))
                .bodyValue(data)
                .exchangeToMono(result -> result.toEntity(String.class))
                .blockOptional().orElseThrow(() -> new KycException(KycErrorCodes.PROXY_FAILED));
        if (response.getStatusCode().is2xxSuccessful() || response.getStatusCode().value() == 424) {
            var active = objectMapper.readTree(response.getBody()).path("peppolActive");
            if (!active.isBoolean()) throw new IllegalStateException("Missing proxy registration state");
            company.setRegisteredOnPeppol(active.asBoolean());
            companyRepository.save(company);
        }
        return response;
    }
}

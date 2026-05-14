package org.letspeppol.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.app.dto.DocumentDto;
import org.letspeppol.app.dto.PageResponse;
import org.letspeppol.app.dto.accountant.CustomerDto;
import org.letspeppol.app.dto.accountant.LinkCustomerDto;
import org.letspeppol.app.service.AccountantService;
import org.letspeppol.app.util.JwtUtil;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/sapi/accountant")
@Tag(name = "App Accountant", description = "Endpoints that support accountant-to-customer linking and access to customer document views.")
@SecurityRequirement(name = "bearerAuth")
public class AccountantController {

    private final AccountantService accountantService;

    /// Accountant tries to link a customer company to itself
    @PostMapping("/link-customer")
    @Operation(summary = "Request customer link", description = "Starts the workflow where an accountant asks to be linked to a customer company.")
    public ResponseEntity<Void> linkCustomer(@AuthenticationPrincipal Jwt jwt, @RequestBody LinkCustomerDto linkCustomerDto) {
        UUID uid = JwtUtil.getUid(jwt);
        String peppolId = JwtUtil.getPeppolId(jwt);
        accountantService.linkCustomer(uid, peppolId, linkCustomerDto);
        return ResponseEntity.ok().build();
    }

    /// Customer confirms the accountant link request
    @PostMapping("/confirm-customer-link")
    @Operation(summary = "Confirm customer link", description = "Confirms a previously requested accountant-customer relationship using the supplied token.")
    public ResponseEntity<Void> confirmLink(@AuthenticationPrincipal Jwt jwt, @RequestParam String token) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        accountantService.confirmLink(peppolId, token);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/customers")
    @Operation(summary = "List linked customers", description = "Returns the customer companies currently linked to the authenticated accountant.")
    public ResponseEntity<List<CustomerDto>> getCustomersForAccountant(@AuthenticationPrincipal Jwt jwt) {
        UUID uid = JwtUtil.getUid(jwt);
        List<CustomerDto> customers = accountantService.getCustomersForAccountant(uid);
        return ResponseEntity.ok(customers);
    }

    @GetMapping("/documents")
    @Operation(summary = "List customer documents", description = "Returns paged documents for one linked customer so accountants can review customer traffic.")
    public PageResponse<DocumentDto> getCustomerDocuments(@AuthenticationPrincipal Jwt jwt, @RequestParam String customerPeppolId, Pageable pageable) {
        UUID uid = JwtUtil.getUid(jwt);
        Page<DocumentDto> page = accountantService.getCustomerDocuments(uid, customerPeppolId, pageable);
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}

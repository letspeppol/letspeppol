package org.letspeppol.app.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.app.dto.accountant.LinkCustomerDto;
import org.letspeppol.app.service.AccountantService;
import org.letspeppol.app.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/sapi/accountant")
public class AccountantController {

    private final AccountantService accountantService;

    /// Accountant tries to link a customer company to itself
    @PostMapping("/link-customer")
    public ResponseEntity<Void> linkCustomer(@AuthenticationPrincipal Jwt jwt, @RequestBody LinkCustomerDto linkCustomerDto) {
        UUID uid = JwtUtil.getUid(jwt);
        String peppolId = JwtUtil.getPeppolId(jwt);
        accountantService.linkCustomer(uid, peppolId, linkCustomerDto);
        return ResponseEntity.ok().build();
    }

    /// Customer confirms the accountant linkage
    @PostMapping("/confirm-customer-link")
    public ResponseEntity<Void> confirmLink(@AuthenticationPrincipal Jwt jwt, @RequestParam String token) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        accountantService.confirmLink(peppolId, token);
        return ResponseEntity.ok().build();
    }

}

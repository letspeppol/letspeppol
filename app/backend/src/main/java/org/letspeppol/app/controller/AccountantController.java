package org.letspeppol.app.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.app.dto.accountant.LinkCustomerDto;
import org.letspeppol.app.service.AccountantService;
import org.letspeppol.app.util.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/sapi/accountant")
public class AccountantController {

    private final AccountantService accountantService;

    @PostMapping("/link-company")
    public ResponseEntity<Void> linkCompany(@AuthenticationPrincipal Jwt jwt, @RequestBody LinkCustomerDto linkCustomerDto) {
        UUID uid = JwtUtil.getUid(jwt);
        String peppolId = JwtUtil.getPeppolId(jwt);
        accountantService.linkCustomer(uid, peppolId, linkCustomerDto);
        return ResponseEntity.ok().build();
    }

}

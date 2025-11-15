package org.letspeppol.app.controller;

import org.letspeppol.app.dto.PartnerDto;
import org.letspeppol.app.service.PartnerService;
import org.letspeppol.app.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/partner")
public class PartnerController {

    private final PartnerService partnerService;

    @GetMapping
    public List<PartnerDto> getParties(@AuthenticationPrincipal Jwt jwt) {
        String companyNumber = JwtUtil.getCompanyNumber(jwt);
        return partnerService.findByCompanyNumber(companyNumber);
    }

    @PutMapping("{id}")
    public PartnerDto updatePartner(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody PartnerDto partnerDto) {
        String companyNumber = JwtUtil.getCompanyNumber(jwt);
        return partnerService.updatePartner(id, partnerDto);
    }

    @PostMapping
    public PartnerDto createPartner(@AuthenticationPrincipal Jwt jwt, @RequestBody PartnerDto partnerDto) {
        String companyNumber = JwtUtil.getCompanyNumber(jwt);
        return partnerService.createPartner(companyNumber, partnerDto);
    }

    @DeleteMapping("{id}")
    public void deletePartner(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        String companyNumber = JwtUtil.getCompanyNumber(jwt);
        partnerService.deletePartner(id);
    }
}

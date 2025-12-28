package org.letspeppol.app.controller;

import org.letspeppol.app.dto.PartnerDto;
import org.letspeppol.app.service.PartnerService;
import org.letspeppol.app.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/sapi/partner")
public class PartnerController {

    private final PartnerService partnerService;

    @GetMapping("/search")
    public ResponseEntity<List<PartnerDto>> search(@AuthenticationPrincipal Jwt jwt, @RequestParam(value = "peppolId", required = true) String peppolId) {
        String ownerPeppolId = JwtUtil.getPeppolId(jwt);
        return ResponseEntity.ok(partnerService.search(ownerPeppolId, peppolId));
    }

    @GetMapping
    public List<PartnerDto> getParties(@AuthenticationPrincipal Jwt jwt) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return partnerService.findByPeppolId(peppolId);
    }

    @PutMapping("{id}")
    public PartnerDto updatePartner(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody PartnerDto partnerDto) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return partnerService.updatePartner(id, partnerDto);
    }

    @PostMapping
    public PartnerDto createPartner(@AuthenticationPrincipal Jwt jwt, @RequestBody PartnerDto partnerDto) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return partnerService.createPartner(peppolId, partnerDto);
    }

    @DeleteMapping("{id}")
    public void deletePartner(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        partnerService.deletePartner(id);
    }
}

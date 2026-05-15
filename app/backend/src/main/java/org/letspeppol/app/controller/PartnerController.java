package org.letspeppol.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "App Partners", description = "Partner master-data endpoints used to manage customers, suppliers, and counterparties inside the app.")
@SecurityRequirement(name = "bearerAuth")
public class PartnerController {

    private final PartnerService partnerService;

    @GetMapping("/search")
    @Operation(summary = "Search partner by Peppol identifier", description = "Looks up an existing partner record for the authenticated company using a Peppol identifier.")
    public ResponseEntity<List<PartnerDto>> search(@AuthenticationPrincipal Jwt jwt, @RequestParam(value = "peppolId", required = true) String peppolId) {
        String ownerPeppolId = JwtUtil.getPeppolId(jwt);
        return ResponseEntity.ok(partnerService.search(ownerPeppolId, peppolId));
    }

    @GetMapping
    @Operation(summary = "List partners", description = "Returns the partner records owned by the authenticated company.")
    public List<PartnerDto> getParties(@AuthenticationPrincipal Jwt jwt) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return partnerService.findByPeppolId(peppolId);
    }

    @PutMapping("{id}")
    @Operation(summary = "Update partner", description = "Updates one partner record used by the authenticated company.")
    public PartnerDto updatePartner(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @RequestBody PartnerDto partnerDto) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return partnerService.updatePartner(id, partnerDto);
    }

    @PostMapping
    @Operation(summary = "Create partner", description = "Creates a new partner record for the authenticated company.")
    public PartnerDto createPartner(@AuthenticationPrincipal Jwt jwt, @RequestBody PartnerDto partnerDto) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        return partnerService.createPartner(peppolId, partnerDto);
    }

    @DeleteMapping("{id}")
    @Operation(summary = "Delete partner", description = "Deletes one partner record from the authenticated company's address book.")
    public void deletePartner(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        String peppolId = JwtUtil.getPeppolId(jwt);
        partnerService.deletePartner(id);
    }
}

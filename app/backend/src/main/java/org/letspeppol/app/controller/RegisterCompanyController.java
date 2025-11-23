package org.letspeppol.app.controller;

import org.letspeppol.app.dto.RegistrationRequest;
import org.letspeppol.app.dto.UnregisterRequest;
import org.letspeppol.app.service.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/internal/company")
public class RegisterCompanyController {

    private final CompanyService companyService;

    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody RegistrationRequest request) {
        companyService.register(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/unregister")
    public ResponseEntity<Void> unregister(@RequestBody UnregisterRequest request) {
        companyService.unregister(request.peppolId());
        return ResponseEntity.ok().build();
    }

}

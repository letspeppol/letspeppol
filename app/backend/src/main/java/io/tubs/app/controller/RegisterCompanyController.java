package io.tubs.app.controller;

import io.tubs.app.dto.RegistrationRequest;
import io.tubs.app.dto.UnregisterRequest;
import io.tubs.app.service.CompanyService;
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
        companyService.unregister(request.companyNumber());
        return ResponseEntity.ok().build();
    }

}

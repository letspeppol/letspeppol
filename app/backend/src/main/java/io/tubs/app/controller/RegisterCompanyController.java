package io.tubs.app.controller;

import io.tubs.app.dto.AppRegistrationRequest;
import io.tubs.app.service.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/internal/company")
public class RegisterCompanyController {

    private final CompanyService companyService;

    @PostMapping("/register")
    public ResponseEntity<Void> register(AppRegistrationRequest request) {
        companyService.register(request);
        return ResponseEntity.ok().build();
    }

}

package org.letspeppol.kyc.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.model.User;
import org.letspeppol.kyc.service.JwtService;
import org.letspeppol.kyc.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@RestController
@RequestMapping("/api/jwt")
@RequiredArgsConstructor
public class AuthController {

    public final static String EAS_ONDERNEMINGSNUMMER = "0208";

    private final JwtService jwtService;
    private final UserService userService;

    @PostMapping("/auth")
    public ResponseEntity<String> auth(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        log.warn("Auth attempt with header: {}", authHeader);
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing or invalid Authorization header");
        }

        String base64Credentials = authHeader.substring("Basic ".length()).trim();
        byte[] credDecoded = Base64.getDecoder().decode(base64Credentials.getBytes(StandardCharsets.UTF_8));
        String credentials = new String(credDecoded, StandardCharsets.UTF_8);

        final String[] values = credentials.split(":", 2);
        if (values.length != 2) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Basic authentication format");
        }
        String email = values[0];
        String password = values[1];
        User user = userService.findUserWithCredentials(email, password);

        String token = jwtService.generateToken(
                EAS_ONDERNEMINGSNUMMER + ":" + user.getCompany().getCompanyNumber(),
                user.getExternalId()
        );

        return ResponseEntity.ok(token);
    }
}

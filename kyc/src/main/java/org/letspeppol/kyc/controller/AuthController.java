package org.letspeppol.kyc.controller;

import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letspeppol.kyc.dto.AuthSwapRequest;
import org.letspeppol.kyc.model.Account;
import org.letspeppol.kyc.model.Ownership;
import org.letspeppol.kyc.service.AccountService;
import org.letspeppol.kyc.service.JwtService;
import org.letspeppol.kyc.service.OwnershipService;
import org.letspeppol.kyc.service.jwt.JwtInfo;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
@RestController
@RequestMapping()
@RequiredArgsConstructor
public class AuthController {

    private final JwtService jwtService;
    private final AccountService accountService;
    private final OwnershipService ownershipService;
    private final Counter authenticationCounterSuccess;
    private final Counter authenticationCounterFailure;

    /// Generates JWT token on login
    @PostMapping("/api/jwt/auth")
    public ResponseEntity<String> auth(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            authenticationCounterFailure.increment();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing or invalid Authorization header");
        }

        String base64Credentials = authHeader.substring("Basic ".length()).trim();
        byte[] credDecoded = Base64.getDecoder().decode(base64Credentials.getBytes(StandardCharsets.UTF_8));
        String credentials = new String(credDecoded, StandardCharsets.UTF_8);

        final String[] values = credentials.split(":", 2);
        if (values.length != 2) {
            authenticationCounterFailure.increment();
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Basic authentication format");
        }
        String emailOrUuid = values[0];
        String password = values[1];
        Account account = accountService.findAccountWithCredentials(emailOrUuid, password); //TODO : Check if account is verified ! Else send info for resend activation mail
        Ownership ownership = account.getOwnerships().getFirst();
        ownershipService.updateLastUsed(ownership);

        String token = jwtService.generateToken(
                ownership.getType(),
                ownership.getCompany().getPeppolId(),
                ownership.getCompany().isPeppolActive(),
                account.getExternalId()
        );
        authenticationCounterSuccess.increment();

        return ResponseEntity.ok(token);
    }

    /// Generates JWT token on swap
    @PostMapping("/sapi/jwt/swap")
    public ResponseEntity<String> swap(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader, @RequestBody AuthSwapRequest request) {
        JwtInfo jwtInfo = jwtService.validateAndGetInfo(authHeader);
        Ownership ownership = ownershipService.getByAccountExternalIdPeppolIdAndType(jwtInfo.uid(), request.peppolId(), request.type());
        ownershipService.updateLastUsed(ownership);

        String token = jwtService.generateToken(
                ownership.getType(),
                ownership.getCompany().getPeppolId(),
                ownership.getCompany().isPeppolActive(),
                jwtInfo.uid()
        );
        authenticationCounterSuccess.increment();

        return ResponseEntity.ok(token);
    }

}

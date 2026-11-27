package org.letspeppol.kyc.controller;

import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.letspeppol.kyc.dto.RevokedToken;
import org.letspeppol.kyc.service.TokenRevocationFeed;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/lapi/revocations")
@Hidden
public class RevocationFeedController {

    private final TokenRevocationFeed tokenRevocationFeed;

    @GetMapping
    public List<RevokedToken> revokedTokens() {
        return tokenRevocationFeed.entries();
    }
}

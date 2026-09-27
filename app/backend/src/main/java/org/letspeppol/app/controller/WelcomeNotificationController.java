package org.letspeppol.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.letspeppol.app.dto.WelcomeNotificationsResponse;
import org.letspeppol.app.service.WelcomeNotificationService;
import org.letspeppol.app.util.JwtUtil;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/sapi/welcome-notifications")
@Tag(name = "App Welcome Notifications", description = "Contextual onboarding notifications for the authenticated company's dashboard.")
@SecurityRequirement(name = "oauth2", scopes = "openid")
public class WelcomeNotificationController {

    private final WelcomeNotificationService welcomeNotificationService;

    @GetMapping
    @Operation(summary = "List welcome notifications", description = "Returns onboarding and configuration prompts that currently apply to the authenticated company.")
    public WelcomeNotificationsResponse getWelcomeNotifications(@AuthenticationPrincipal Jwt jwt) {
        return welcomeNotificationService.getNotificationsForCompany(JwtUtil.getPeppolId(jwt));
    }
}

package org.letspeppol.app.controller;

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
public class WelcomeNotificationController {

    private final WelcomeNotificationService welcomeNotificationService;

    @GetMapping
    public WelcomeNotificationsResponse getWelcomeNotifications(@AuthenticationPrincipal Jwt jwt) {
        return welcomeNotificationService.getNotificationsForCompany(JwtUtil.getPeppolId(jwt));
    }
}

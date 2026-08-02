package org.letspeppol.kyc.service;

import org.junit.jupiter.api.Test;
import org.letspeppol.kyc.exception.TooManyRequestsException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimiterServiceTest {

    private RateLimiterService newLimiter(int loginMax, long loginWindow, int emailMax, long emailWindow) {
        RateLimiterService s = new RateLimiterService();
        ReflectionTestUtils.setField(s, "loginMax", loginMax);
        ReflectionTestUtils.setField(s, "loginWindowSeconds", loginWindow);
        ReflectionTestUtils.setField(s, "emailMax", emailMax);
        ReflectionTestUtils.setField(s, "emailWindowSeconds", emailWindow);
        return s;
    }

    @Test
    void allowsUpToLimitThenBlocksLogin() {
        RateLimiterService s = newLimiter(3, 300, 5, 3600);
        s.checkLogin("user@example.com");
        s.checkLogin("user@example.com");
        s.checkLogin("user@example.com");
        assertThrows(TooManyRequestsException.class, () -> s.checkLogin("user@example.com"));
    }

    @Test
    void differentIdentifiersAreIndependent() {
        RateLimiterService s = newLimiter(2, 300, 5, 3600);
        s.checkLogin("a@example.com");
        s.checkLogin("a@example.com");
        assertThrows(TooManyRequestsException.class, () -> s.checkLogin("a@example.com"));
        assertDoesNotThrow(() -> s.checkLogin("b@example.com"));
    }

    @Test
    void identifierIsCaseInsensitive() {
        RateLimiterService s = newLimiter(2, 300, 5, 3600);
        s.checkLogin("User@Example.com");
        s.checkLogin("user@example.com");
        assertThrows(TooManyRequestsException.class, () -> s.checkLogin("USER@EXAMPLE.COM"));
    }

    @Test
    void activationUsesEmailBudget() {
        RateLimiterService s = newLimiter(10, 300, 2, 3600);
        s.checkActivation("c@example.com");
        s.checkActivation("c@example.com");
        assertThrows(TooManyRequestsException.class, () -> s.checkActivation("c@example.com"));
    }

    @Test
    void passwordResetUsesEmailBudget() {
        RateLimiterService s = newLimiter(10, 300, 2, 3600);
        s.checkPasswordReset("d@example.com");
        s.checkPasswordReset("d@example.com");
        assertThrows(TooManyRequestsException.class, () -> s.checkPasswordReset("d@example.com"));
    }
}

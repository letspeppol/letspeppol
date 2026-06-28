package org.letspeppol.kyc.controller;

import jakarta.servlet.http.HttpSession;
import org.letspeppol.kyc.config.TotpAuthenticationSuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginPageController {

    @Value("${UI_URL:http://localhost:9000}")
    private String uiBaseUrl;

    @GetMapping("/login")
    public String login(Model model, @RequestParam(value = "error", required = false) String error) {
        model.addAttribute("uiBaseUrl", uiBaseUrl);
        model.addAttribute("error", error != null);
        return "login";
    }

    @GetMapping("/totp-verify")
    public String totpVerify(HttpSession session, Model model,
                             @RequestParam(value = "error", required = false) String error) {
        if (session.getAttribute(TotpAuthenticationSuccessHandler.TOTP_PENDING_ACCOUNT_ID) == null) {
            return "redirect:/login";
        }
        model.addAttribute("uiBaseUrl", uiBaseUrl);
        model.addAttribute("error", error != null);
        return "totp-verify";
    }
}

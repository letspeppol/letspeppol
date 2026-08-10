package org.letspeppol.kyc.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class LoginRedirectController {

    private final String uiLoginUrl;

    public LoginRedirectController(@Value("${UI_URL:http://localhost:9000}") String uiBaseUrl) {
        this.uiLoginUrl = uiBaseUrl.replaceAll("/+$", "") + "/login";
    }

    @GetMapping({"/login", "/totp-verify"})
    public RedirectView login(@RequestParam(value = "error", required = false) String error) {
        String target = error == null ? uiLoginUrl : uiLoginUrl + "?error";
        RedirectView redirect = new RedirectView(target);
        redirect.setExposeModelAttributes(false);
        return redirect;
    }
}

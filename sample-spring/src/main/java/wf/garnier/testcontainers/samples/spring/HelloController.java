package wf.garnier.testcontainers.samples.spring;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * @author Daniel Garnier-Moiroux
 */
@Controller
class HelloController {

    @GetMapping("/")
    public String hello(Model model, @AuthenticationPrincipal OidcUser user) {
        model.addAttribute("name", user.getClaim("name"));
        model.addAttribute("email", user.getEmail());
        model.addAttribute("subject", user.getSubject());
        return "public";
    }
}

package io.sentrius.sso.controllers.view;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Slf4j
@Controller
@RequestMapping("")
public class HomeViewController {

    @GetMapping
    public String home() {
        return "redirect:/sso/v1/dashboard";
    }
}

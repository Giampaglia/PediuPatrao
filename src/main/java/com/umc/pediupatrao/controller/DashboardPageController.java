package com.umc.pediupatrao.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Tela do dashboard. Os dados são carregados pelo navegador a partir de /api/dashboard. */
@Controller
public class DashboardPageController {

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("content", "dashboard :: content");
        return "layout";
    }
}

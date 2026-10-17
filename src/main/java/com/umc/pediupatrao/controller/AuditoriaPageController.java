package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.service.AuditoriaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuditoriaPageController {

    private final AuditoriaService auditoriaService;

    public AuditoriaPageController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/auditoria")
    public String auditoria(@RequestParam(required = false) String entidade,
            @RequestParam(required = false) String entidadeId,
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) TipoOperacao operacao,
            Model model) {
        model.addAttribute("registros", auditoriaService.consultar(entidade, entidadeId, usuario, operacao));
        model.addAttribute("operacoes", TipoOperacao.values());
        model.addAttribute("entidade", entidade);
        model.addAttribute("entidadeId", entidadeId);
        model.addAttribute("usuario", usuario);
        model.addAttribute("operacao", operacao);
        model.addAttribute("content", "auditoria/lista :: content");
        return "layout";
    }
}

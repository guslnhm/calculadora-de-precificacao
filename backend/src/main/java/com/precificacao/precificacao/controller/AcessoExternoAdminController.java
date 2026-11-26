package com.precificacao.precificacao.controller;

import com.precificacao.precificacao.acesso.Sessao;
import com.precificacao.precificacao.externo.AcessoExternoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Gestão dos acessos de fora (só equipe; o filtro barra mentorados em /acessos). */
@RestController
@RequestMapping("/acessos/externo")
public class AcessoExternoAdminController {

    private final AcessoExternoService service;

    public AcessoExternoAdminController(AcessoExternoService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> listar() {
        return Map.of("acessos", service.listar(), "lojas", service.lojasDisponiveis());
    }

    @PostMapping
    public AcessoExternoService.ComChave criar(@RequestBody AcessoExternoService.Pedido req, HttpServletRequest http) {
        Sessao s = Sessao.de(http);
        return service.criar(req, s == null ? null : s.nome());
    }

    @PutMapping("/{id}")
    public AcessoExternoService.Acesso editar(@PathVariable long id, @RequestBody AcessoExternoService.Edicao req) {
        return service.editar(id, req);
    }

    @PostMapping("/{id}/nova-chave")
    public AcessoExternoService.ComChave novaChave(@PathVariable long id) {
        return service.trocarChave(id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> excluir(@PathVariable long id) {
        service.excluir(id);
        return Map.of("ok", true);
    }
}

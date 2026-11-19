package com.precificacao.precificacao.controller;

import com.precificacao.precificacao.acesso.AcessoService;
import com.precificacao.precificacao.acesso.Sessao;
import com.precificacao.precificacao.painel.PainelService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Gestão de acessos (só equipe; o filtro barra mentorados). */
@RestController
@RequestMapping("/acessos")
public class AcessosController {

    private final AcessoService acessos;
    private final PainelService painel;

    public AcessosController(AcessoService acessos, PainelService painel) {
        this.acessos = acessos;
        this.painel = painel;
    }

    @GetMapping("/lojas")
    public List<PainelService.OpcaoLoja> lojas() {
        return painel.opcoesLojas();
    }

    @GetMapping("/convites")
    public List<AcessoService.Convite> convites() {
        return acessos.convites();
    }

    @PostMapping("/convites")
    public AcessoService.Convite criarConvite(@RequestBody AcessoService.NovoConvite req, HttpServletRequest http) {
        Sessao s = Sessao.de(http);
        return acessos.criarConvite(req, s == null ? null : s.nome());
    }

    @DeleteMapping("/convites/{id}")
    public Map<String, Object> revogar(@PathVariable long id) {
        acessos.revogarConvite(id);
        return Map.of("ok", true);
    }

    @GetMapping("/usuarios")
    public List<AcessoService.Usuario> usuarios() {
        return acessos.usuarios();
    }

    @PutMapping("/usuarios/{id}")
    public Map<String, Object> editar(@PathVariable long id, @RequestBody AcessoService.EdicaoUsuario req,
                                      HttpServletRequest http) {
        acessos.editarUsuario(id, req, Sessao.de(http));
        return Map.of("ok", true);
    }

    @PostMapping("/usuarios/{id}/link-senha")
    public Map<String, Object> linkSenha(@PathVariable long id) {
        return Map.of("token", acessos.gerarLinkSenha(id));
    }

    @GetMapping("/adesao")
    public AcessoService.Adesao adesao() {
        return acessos.adesao();
    }
}

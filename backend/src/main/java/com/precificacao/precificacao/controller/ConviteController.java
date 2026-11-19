package com.precificacao.precificacao.controller;

import com.precificacao.precificacao.acesso.AcessoService;
import com.precificacao.precificacao.dto.LoginResponseDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Rotas públicas (sem login): cadastro pelo link de convite e link de nova senha. */
@RestController
public class ConviteController {

    public record NovaSenha(String senha) {
    }

    private final AcessoService acessos;

    public ConviteController(AcessoService acessos) {
        this.acessos = acessos;
    }

    @GetMapping("/convites/{token}")
    public AcessoService.ConvitePublico convite(@PathVariable String token) {
        return acessos.conviteParaCadastro(token);
    }

    @PostMapping("/convites/{token}/aceitar")
    public LoginResponseDTO aceitar(@PathVariable String token, @RequestBody AcessoService.Cadastro cadastro) {
        return acessos.aceitar(token, cadastro);
    }

    @GetMapping("/senha/{token}")
    public Map<String, Object> linkSenha(@PathVariable String token) {
        return acessos.linkSenha(token);
    }

    @PostMapping("/senha/{token}")
    public LoginResponseDTO novaSenha(@PathVariable String token, @RequestBody NovaSenha body) {
        return acessos.usarLinkSenha(token, body.senha());
    }
}

package com.precificacao.precificacao.controller;

import com.precificacao.precificacao.acesso.Sessao;
import com.precificacao.precificacao.dto.LoginRequestDTO;
import com.precificacao.precificacao.dto.LoginResponseDTO;
import com.precificacao.precificacao.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    public record TrocaSenha(String atual, String nova) {
    }

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponseDTO login(@Valid @RequestBody LoginRequestDTO dto) {
        return authService.login(dto);
    }

    /** Quem está logado (o site usa para decidir o que mostrar). */
    @GetMapping("/eu")
    public Map<String, Object> eu(HttpServletRequest req) {
        Sessao s = Sessao.de(req);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nome", s.nome());
        m.put("email", s.email());
        m.put("perfil", s.perfil());
        m.put("mentorado", s.mentorado());
        m.put("lojas", s.lojas());
        m.put("trocarSenha", s.trocarSenha());
        return m;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest req) {
        authService.sair(Sessao.de(req));
        return Map.of("ok", true);
    }

    @PutMapping("/senha")
    public Map<String, Object> trocarSenha(@RequestBody TrocaSenha body, HttpServletRequest req) {
        authService.trocarSenha(Sessao.de(req), body.atual(), body.nova());
        return Map.of("ok", true);
    }
}

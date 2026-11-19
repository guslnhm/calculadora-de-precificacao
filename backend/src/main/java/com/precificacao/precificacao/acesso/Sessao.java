package com.precificacao.precificacao.acesso;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

/** Quem está logado nesta requisição (o filtro guarda no atributo "sessao"). */
public record Sessao(long usuarioId, String nome, String email, String perfil, Set<String> lojas,
                     boolean trocarSenha, String tokenHash) {

    public static final String ATRIBUTO = "sessao";
    public static final String MENTORADO = "MENTORADO";
    public static final String EQUIPE = "EQUIPE";

    /** Mentorado: só vê o painel das próprias lojas. Qualquer outro perfil é da equipe (acesso total). */
    public boolean mentorado() {
        return MENTORADO.equalsIgnoreCase(perfil);
    }

    public static Sessao de(HttpServletRequest req) {
        Object s = req.getAttribute(ATRIBUTO);
        return s instanceof Sessao sessao ? sessao : null;
    }

    public static boolean ehMentorado(HttpServletRequest req) {
        Sessao s = de(req);
        return s != null && s.mentorado();
    }
}

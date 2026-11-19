package com.precificacao.precificacao.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precificacao.precificacao.acesso.Sessao;
import com.precificacao.precificacao.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Exige login em tudo, menos no login e nas páginas públicas de convite/nova senha.
 * Mentorado só passa nas rotas do painel de leitura e só para as lojas dele: o resto devolve 403.
 */
@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    private final AuthService authService;
    private final ObjectMapper json = new ObjectMapper();

    public AuthTokenFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = caminho(request);
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.equals("/auth/login")
                || path.startsWith("/convites/")
                || path.startsWith("/senha/")
                || path.equals("/")
                || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Sessao s = authService.sessao(request.getHeader("Authorization"));
        if (s == null) {
            erro(response, HttpServletResponse.SC_UNAUTHORIZED, "NAO_AUTENTICADO",
                    "Sua sessão terminou. Entre de novo.");
            return;
        }
        if (s.mentorado() && !permitidoAoMentorado(request, s)) {
            erro(response, HttpServletResponse.SC_FORBIDDEN, "SEM_PERMISSAO", "Você não tem acesso a isso");
            return;
        }
        request.setAttribute(Sessao.ATRIBUTO, s);
        request.setAttribute("usuarioNome", s.nome());
        chain.doFilter(request, response);
    }

    static boolean permitidoAoMentorado(HttpServletRequest req, Sessao s) {
        String path = caminho(req);
        String metodo = req.getMethod();
        if ("GET".equals(metodo)) {
            if (path.equals("/painel/carteira") || path.equals("/auth/eu")) {
                return true;
            }
            if (path.equals("/painel/loja")) {
                String chave = req.getParameter("chave");
                return chave != null && s.lojas().contains(chave);
            }
            return false;
        }
        return ("POST".equals(metodo) && path.equals("/auth/logout"))
                || ("PUT".equals(metodo) && path.equals("/auth/senha"));
    }

    /**
     * Caminho já normalizado pelo servidor (servletPath + pathInfo, sem "../" nem ";..."). Nos testes (MockMvc)
     * o servletPath vem vazio; aí usa a URI.
     */
    static String caminho(HttpServletRequest req) {
        String sp = req.getServletPath();
        String pi = req.getPathInfo();
        String p = (sp == null ? "" : sp) + (pi == null ? "" : pi);
        if (p.isEmpty()) {
            String uri = req.getRequestURI();
            String ctx = req.getContextPath();
            p = ctx != null && !ctx.isEmpty() && uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;
        }
        return p;
    }

    private void erro(HttpServletResponse response, int status, String codigo, String mensagem) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(), Map.of("erro", codigo, "mensagem", mensagem));
    }
}

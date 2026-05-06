package com.precificacao.precificacao.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.precificacao.precificacao.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
public class AuthTokenFilter extends OncePerRequestFilter {

    private final AuthService authService;

    public AuthTokenFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();

        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.startsWith("/auth/")
                || path.equals("/")
                || path.equals("/error");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String token = request.getHeader("Authorization");

        if (token == null || token.isBlank() || !authService.tokenValido(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            new ObjectMapper().writeValue(
                    response.getWriter(),
                    Map.of(
                            "erro", "NAO_AUTENTICADO",
                            "mensagem", "Acesso não autorizado"
                    )
            );
            return;
        }

        filterChain.doFilter(request, response);
    }
}
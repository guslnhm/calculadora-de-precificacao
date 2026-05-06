package com.precificacao.precificacao.service;

import com.precificacao.precificacao.dto.LoginRequestDTO;
import com.precificacao.precificacao.dto.LoginResponseDTO;
import com.precificacao.precificacao.entity.Usuario;
import com.precificacao.precificacao.exception.CredenciaisInvalidasException;
import com.precificacao.precificacao.repository.UsuarioRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;

    private final Map<String, Long> tokensAtivos = new ConcurrentHashMap<>();

    public AuthService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    public LoginResponseDTO login(LoginRequestDTO dto) {
        Usuario usuario = usuarioRepository.findByEmailAndAtivoTrue(dto.getEmail())
                .orElseThrow(CredenciaisInvalidasException::new);

        if (!usuario.getSenhaHash().equals(dto.getSenha())) {
            throw new CredenciaisInvalidasException();
        }

        String token = UUID.randomUUID().toString();
        tokensAtivos.put(token, usuario.getId());

        LoginResponseDTO response = new LoginResponseDTO();
        response.setToken(token);
        response.setNome(usuario.getNome());
        response.setPerfil(usuario.getPerfil());

        return response;
    }

    public boolean tokenValido(String token) {
        return token != null && tokensAtivos.containsKey(token);
    }
}
package com.precificacao.precificacao.controller;

import com.precificacao.precificacao.dto.LoginRequestDTO;
import com.precificacao.precificacao.dto.LoginResponseDTO;
import com.precificacao.precificacao.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponseDTO login(@Valid @RequestBody LoginRequestDTO dto) {
        return authService.login(dto);
    }
}
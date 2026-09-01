package com.precificacao.precificacao.controller;

import com.precificacao.precificacao.dto.LojaPlataformasRequestDTO;
import com.precificacao.precificacao.dto.LojaPlataformasResponseDTO;
import com.precificacao.precificacao.service.LojaPlataformaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/lojas/{lojaId}/plataformas")
public class LojaPlataformaController {

    private final LojaPlataformaService lojaPlataformaService;

    public LojaPlataformaController(
            LojaPlataformaService lojaPlataformaService
    ) {
        this.lojaPlataformaService = lojaPlataformaService;
    }

    @GetMapping
    public LojaPlataformasResponseDTO buscar(
            @PathVariable Long lojaId
    ) {
        return lojaPlataformaService.buscar(lojaId);
    }

    @PutMapping
    public LojaPlataformasResponseDTO atualizar(
            @PathVariable Long lojaId,
            @Valid @RequestBody LojaPlataformasRequestDTO dto
    ) {
        return lojaPlataformaService.atualizar(lojaId, dto);
    }
}
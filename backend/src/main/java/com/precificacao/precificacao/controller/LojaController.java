package com.precificacao.precificacao.controller;

import com.precificacao.precificacao.dto.LojaPercentuaisDTO;
import com.precificacao.precificacao.dto.LojaRequestDTO;
import com.precificacao.precificacao.dto.LojaResponseDTO;
import com.precificacao.precificacao.service.LojaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.precificacao.precificacao.enums.Plataforma;
import com.precificacao.precificacao.dto.LojaAtualizacaoDTO;

import java.util.List;

@RestController
@RequestMapping("/lojas")
public class LojaController {

    private final LojaService lojaService;

    public LojaController(LojaService lojaService) {
        this.lojaService = lojaService;
    }

    @GetMapping
    public List<LojaResponseDTO> listar(
            @RequestParam(required = false)
            Plataforma plataforma
    ) {
        return lojaService.listar(plataforma);
    }

    @PostMapping
    public LojaResponseDTO criar(@Valid @RequestBody LojaRequestDTO lojaRequestDTO) {
        return lojaService.criar(lojaRequestDTO);
    }

    @GetMapping("/{id}/percentuais")
    public LojaPercentuaisDTO buscarPercentuais(@PathVariable Long id) {
        return lojaService.buscarPercentuais(id);
    }

    @PutMapping("/{id}/percentuais")
    public LojaPercentuaisDTO salvarPercentuais(
            @PathVariable Long id,
            @Valid @RequestBody LojaPercentuaisDTO dto
    ) {
        return lojaService.salvarPercentuais(id, dto);
    }
    
    @DeleteMapping("/{id}")
    public void desativar(@PathVariable Long id) {
        lojaService.desativar(id);
    }

    @PutMapping("/{id}")
    public LojaResponseDTO atualizar(
            @PathVariable Long id,
            @Valid @RequestBody LojaAtualizacaoDTO dto
    ) {
        return lojaService.atualizar(id, dto);
    }
}
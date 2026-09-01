package com.precificacao.precificacao.controller;

import com.precificacao.precificacao.dto.ConfiguracaoPlataformaRequestDTO;
import com.precificacao.precificacao.dto.ConfiguracaoPlataformaResponseDTO;
import com.precificacao.precificacao.enums.Plataforma;
import com.precificacao.precificacao.service.LojaConfiguracaoPlataformaService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/lojas/{lojaId}/configuracoes")
public class LojaConfiguracaoPlataformaController {

    private final LojaConfiguracaoPlataformaService configuracaoService;

    public LojaConfiguracaoPlataformaController(
            LojaConfiguracaoPlataformaService configuracaoService
    ) {
        this.configuracaoService = configuracaoService;
    }

    @GetMapping("/{plataforma}")
    public ConfiguracaoPlataformaResponseDTO buscar(
            @PathVariable Long lojaId,
            @PathVariable Plataforma plataforma
    ) {
        return configuracaoService.buscar(lojaId, plataforma);
    }

    @PutMapping
    public ConfiguracaoPlataformaResponseDTO salvar(
            @PathVariable Long lojaId,
            @Valid @RequestBody ConfiguracaoPlataformaRequestDTO dto
    ) {
        return configuracaoService.salvar(lojaId, dto);
    }
}
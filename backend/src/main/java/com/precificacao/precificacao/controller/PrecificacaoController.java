package com.precificacao.precificacao.controller;

import com.precificacao.precificacao.dto.SimulacaoRequestDTO;
import com.precificacao.precificacao.dto.SimulacaoResponseDTO;
import com.precificacao.precificacao.service.PrecificacaoService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.precificacao.precificacao.dto.SimulacaoReversaRequestDTO;
import com.precificacao.precificacao.dto.SimulacaoReversaResponseDTO;

@RestController
@RequestMapping("/precificacao")
public class PrecificacaoController {

    private final PrecificacaoService precificacaoService;

    public PrecificacaoController(PrecificacaoService precificacaoService) {
        this.precificacaoService = precificacaoService;
    }

    @PostMapping("/simular")
    public SimulacaoResponseDTO simular(@Valid @RequestBody SimulacaoRequestDTO dto) {
        return precificacaoService.simular(dto);
    }

    @PostMapping("/reversa")
    public SimulacaoReversaResponseDTO simularReversa(
            @Valid @RequestBody SimulacaoReversaRequestDTO dto
    ) {
        return precificacaoService.simularReversa(dto);
    }

}
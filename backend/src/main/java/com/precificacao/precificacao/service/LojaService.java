package com.precificacao.precificacao.service;

import com.precificacao.precificacao.dto.LojaPercentuaisDTO;
import com.precificacao.precificacao.dto.LojaRequestDTO;
import com.precificacao.precificacao.dto.LojaResponseDTO;
import com.precificacao.precificacao.entity.Loja;
import com.precificacao.precificacao.repository.LojaRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class LojaService {

    private final LojaRepository lojaRepository;

    public LojaService(LojaRepository lojaRepository) {
        this.lojaRepository = lojaRepository;
    }

    public List<LojaResponseDTO> listar(){
        return lojaRepository.findAll()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public LojaResponseDTO criar(LojaRequestDTO dto){
        Loja loja = new Loja();
        loja.setNome(dto.getNome());
        loja.setClienteNome(dto.getClienteNome());
        loja.setObservacao(dto.getObservacao());
        loja.setAtivo(true);
        loja.setCriadoEm(LocalDateTime.now());
        loja.setAtualizadoEm(LocalDateTime.now());

        Loja lojaSalva = lojaRepository.save(loja);
        return toResponseDTO(lojaSalva);
    }

    public LojaPercentuaisDTO buscarPercentuais(Long lojaId) {
        Loja loja = lojaRepository.findById(lojaId)
                .orElseThrow(() -> new RuntimeException("Loja não encontrada"));

        LojaPercentuaisDTO dto = new LojaPercentuaisDTO();
        dto.setImposto(loja.getPercentualImposto());
        dto.setCustoFixo(loja.getPercentualCustoFixo());
        dto.setTaxaTransacao(loja.getPercentualTaxaTransacao());
        dto.setTaxaIfood(loja.getPercentualTaxaIfood());
        dto.setTaxaRepasse(loja.getPercentualTaxaRepasse());
        dto.setLucro(loja.getPercentualLucro());
        dto.setTaxaFranquia(loja.getPercentualTaxaFranquia());

        return dto;
    }

    public LojaPercentuaisDTO salvarPercentuais(Long lojaId, LojaPercentuaisDTO dto) {
        Loja loja = lojaRepository.findById(lojaId)
                .orElseThrow(() -> new RuntimeException("Loja não encontrada"));

        BigDecimal imposto = valorOuZero(dto.getImposto());
        BigDecimal custoFixo = valorOuZero(dto.getCustoFixo());
        BigDecimal taxaTransacao = valorOuZero(dto.getTaxaTransacao());
        BigDecimal taxaIfood = valorOuZero(dto.getTaxaIfood());
        BigDecimal taxaRepasse = valorOuZero(dto.getTaxaRepasse());
        BigDecimal lucro = valorOuZero(dto.getLucro());
        BigDecimal taxaFranquia = valorOuZero(dto.getTaxaFranquia());

        BigDecimal soma = imposto
                .add(custoFixo)
                .add(taxaTransacao)
                .add(taxaIfood)
                .add(taxaRepasse)
                .add(lucro)
                .add(taxaFranquia);

        if (soma.compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new RuntimeException("A soma dos percentuais não pode ser maior ou igual a 100%");
        }

        loja.setPercentualImposto(imposto);
        loja.setPercentualCustoFixo(custoFixo);
        loja.setPercentualTaxaTransacao(taxaTransacao);
        loja.setPercentualTaxaIfood(taxaIfood);
        loja.setPercentualTaxaRepasse(taxaRepasse);
        loja.setPercentualLucro(lucro);
        loja.setAtualizadoEm(LocalDateTime.now());
        loja.setPercentualTaxaFranquia(taxaFranquia);

        lojaRepository.save(loja);

        LojaPercentuaisDTO response = new LojaPercentuaisDTO();
        response.setImposto(loja.getPercentualImposto());
        response.setCustoFixo(loja.getPercentualCustoFixo());
        response.setTaxaTransacao(loja.getPercentualTaxaTransacao());
        response.setTaxaIfood(loja.getPercentualTaxaIfood());
        response.setTaxaRepasse(loja.getPercentualTaxaRepasse());
        response.setLucro(loja.getPercentualLucro());
        response.setTaxaFranquia(loja.getPercentualTaxaFranquia());

        return response;
    }

    private BigDecimal valorOuZero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private LojaResponseDTO toResponseDTO(Loja loja){
        LojaResponseDTO dto = new LojaResponseDTO();
        dto.setId(loja.getId());
        dto.setNome(loja.getNome());
        dto.setClienteNome(loja.getClienteNome());
        dto.setObservacao(loja.getObservacao());
        dto.setAtivo(loja.getAtivo());
        dto.setCriadoEm(loja.getCriadoEm());
        dto.setAtualizadoEm(loja.getAtualizadoEm());
        return dto;
    }
}
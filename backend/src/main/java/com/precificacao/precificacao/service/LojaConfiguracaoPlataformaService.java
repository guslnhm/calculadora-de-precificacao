package com.precificacao.precificacao.service;

import com.precificacao.precificacao.dto.ConfiguracaoPlataformaRequestDTO;
import com.precificacao.precificacao.dto.ConfiguracaoPlataformaResponseDTO;
import com.precificacao.precificacao.entity.Loja;
import com.precificacao.precificacao.entity.LojaConfiguracaoPlataforma;
import com.precificacao.precificacao.enums.Plataforma;
import com.precificacao.precificacao.repository.LojaConfiguracaoPlataformaRepository;
import com.precificacao.precificacao.repository.LojaRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class LojaConfiguracaoPlataformaService {

    private final LojaConfiguracaoPlataformaRepository configuracaoRepository;
    private final LojaRepository lojaRepository;

    public LojaConfiguracaoPlataformaService(
            LojaConfiguracaoPlataformaRepository configuracaoRepository,
            LojaRepository lojaRepository
    ) {
        this.configuracaoRepository = configuracaoRepository;
        this.lojaRepository = lojaRepository;
    }

    public ConfiguracaoPlataformaResponseDTO buscar(
            Long lojaId,
            Plataforma plataforma
    ) {

        Loja loja = lojaRepository.findById(lojaId)
                .orElseThrow(() -> new RuntimeException("Loja não encontrada"));

        return configuracaoRepository
                .findByLojaIdAndPlataforma(lojaId, plataforma)
                .map(this::toResponseDTO)
                .orElseGet(() -> configuracaoVazia(loja, plataforma));
    }

    public ConfiguracaoPlataformaResponseDTO salvar(
            Long lojaId,
            ConfiguracaoPlataformaRequestDTO dto
    ) {

        Loja loja = lojaRepository.findById(lojaId)
                .orElseThrow(() -> new RuntimeException("Loja não encontrada"));

        BigDecimal imposto =
                valorOuZero(dto.getPercentualImposto());

        BigDecimal custoFixo =
                valorOuZero(dto.getPercentualCustoFixo());

        BigDecimal taxaPlataforma =
                valorOuZero(dto.getPercentualTaxaPlataforma());

        BigDecimal taxaFranquia =
                valorOuZero(dto.getPercentualTaxaFranquia());

        BigDecimal taxaTransacao =
                valorOuZero(dto.getPercentualTaxaTransacao());

        BigDecimal taxaAntecipacao =
                valorOuZero(dto.getPercentualTaxaAntecipacao());

        BigDecimal lucro =
                valorOuZero(dto.getPercentualLucro());

        BigDecimal soma = imposto
                .add(custoFixo)
                .add(taxaPlataforma)
                .add(taxaFranquia)
                .add(taxaTransacao)
                .add(taxaAntecipacao)
                .add(lucro);

        if (soma.compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new RuntimeException(
                    "A soma dos percentuais não pode ser maior ou igual a 100%"
            );
        }

        LojaConfiguracaoPlataforma configuracao =
                configuracaoRepository
                        .findByLojaIdAndPlataforma(
                                lojaId,
                                dto.getPlataforma()
                        )
                        .orElseGet(LojaConfiguracaoPlataforma::new);

        /*
         * Se já existir:
         * apenas atualizamos os percentuais.
         *
         * Se ainda não existir:
         * definimos loja e plataforma e fazemos o primeiro INSERT.
         */
        if (configuracao.getId() == null) {
            configuracao.setLoja(loja);
            configuracao.setPlataforma(dto.getPlataforma());
        }

        configuracao.setPercentualImposto(imposto);
        configuracao.setPercentualCustoFixo(custoFixo);
        configuracao.setPercentualTaxaPlataforma(taxaPlataforma);
        configuracao.setPercentualTaxaFranquia(taxaFranquia);
        configuracao.setPercentualTaxaTransacao(taxaTransacao);
        configuracao.setPercentualTaxaAntecipacao(taxaAntecipacao);
        configuracao.setPercentualLucro(lucro);

        configuracao.setAtivo(true);

        LojaConfiguracaoPlataforma salva =
                configuracaoRepository.save(configuracao);

        return toResponseDTO(salva);
    }

    private ConfiguracaoPlataformaResponseDTO configuracaoVazia(
            Loja loja,
            Plataforma plataforma
    ) {

        ConfiguracaoPlataformaResponseDTO dto =
                new ConfiguracaoPlataformaResponseDTO();

        dto.setId(null);

        dto.setLojaId(loja.getId());
        dto.setNomeLoja(loja.getNome());

        dto.setPlataforma(plataforma);

        dto.setPercentualImposto(BigDecimal.ZERO);
        dto.setPercentualCustoFixo(BigDecimal.ZERO);
        dto.setPercentualTaxaPlataforma(BigDecimal.ZERO);
        dto.setPercentualTaxaFranquia(BigDecimal.ZERO);
        dto.setPercentualTaxaTransacao(BigDecimal.ZERO);
        dto.setPercentualTaxaAntecipacao(BigDecimal.ZERO);
        dto.setPercentualLucro(BigDecimal.ZERO);

        dto.setAtivo(false);

        return dto;
    }

    private ConfiguracaoPlataformaResponseDTO toResponseDTO(
            LojaConfiguracaoPlataforma configuracao
    ) {

        ConfiguracaoPlataformaResponseDTO dto =
                new ConfiguracaoPlataformaResponseDTO();

        dto.setId(configuracao.getId());

        dto.setLojaId(configuracao.getLoja().getId());
        dto.setNomeLoja(configuracao.getLoja().getNome());

        dto.setPlataforma(configuracao.getPlataforma());

        dto.setPercentualImposto(
                configuracao.getPercentualImposto()
        );

        dto.setPercentualCustoFixo(
                configuracao.getPercentualCustoFixo()
        );

        dto.setPercentualTaxaPlataforma(
                configuracao.getPercentualTaxaPlataforma()
        );

        dto.setPercentualTaxaFranquia(
                configuracao.getPercentualTaxaFranquia()
        );

        dto.setPercentualTaxaTransacao(
                configuracao.getPercentualTaxaTransacao()
        );

        dto.setPercentualTaxaAntecipacao(
                configuracao.getPercentualTaxaAntecipacao()
        );

        dto.setPercentualLucro(
                configuracao.getPercentualLucro()
        );

        dto.setAtivo(configuracao.getAtivo());

        return dto;
    }

    private BigDecimal valorOuZero(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }
}
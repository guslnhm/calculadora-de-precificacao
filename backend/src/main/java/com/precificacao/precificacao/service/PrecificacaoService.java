package com.precificacao.precificacao.service;

import com.precificacao.precificacao.dto.SimulacaoRequestDTO;
import com.precificacao.precificacao.dto.SimulacaoResponseDTO;
import com.precificacao.precificacao.entity.Item;
import com.precificacao.precificacao.repository.ItemRepository;
import org.springframework.stereotype.Service;
import com.precificacao.precificacao.exception.*;
import com.precificacao.precificacao.dto.SimulacaoReversaRequestDTO;
import com.precificacao.precificacao.dto.SimulacaoReversaResponseDTO;
import com.precificacao.precificacao.dto.Simulacao99RequestDTO;
import com.precificacao.precificacao.dto.Simulacao99ResponseDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PrecificacaoService {

    private final ItemRepository itemRepository;

    public PrecificacaoService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    public SimulacaoResponseDTO simular(SimulacaoRequestDTO dto) {
        Item item = itemRepository.findById(dto.getItemId())
                .orElseThrow(() -> new ItemNaoEncontradoException(dto.getItemId()));

        BigDecimal cmv = item.getCmv();
        BigDecimal frete = dto.getFrete();

        BigDecimal cmvMaisFrete = cmv.add(frete);

        BigDecimal percentualTotal = dto.getImposto()
                .add(dto.getTaxaFranquia())
                .add(dto.getCustoFixo())
                .add(dto.getTaxaTransacao())
                .add(dto.getTaxaIfood())
                .add(dto.getTaxaRepasse())
                .add(dto.getLucro());

        if (percentualTotal.compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new PercentualInvalidoException();
        }

        BigDecimal percentualDecimal = percentualTotal.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal coeficienteC = BigDecimal.ONE.subtract(percentualDecimal);

        BigDecimal valorBase = cmvMaisFrete.divide(coeficienteC, 2, RoundingMode.HALF_UP);

        SimulacaoResponseDTO response = new SimulacaoResponseDTO();
        response.setItemId(item.getId());
        response.setNomeItem(item.getNomeItem());
        response.setLojaId(item.getLoja().getId());
        response.setNomeLoja(item.getLoja().getNome());
        response.setCmv(cmv);
        response.setFrete(frete);
        response.setCmvMaisFrete(cmvMaisFrete);
        response.setPercentualTotal(percentualTotal);
        response.setCoeficienteC(coeficienteC);
        response.setValorBase(valorBase);
        response.setValorCupom5(valorBase.add(BigDecimal.valueOf(5)));
        response.setValorCupom8(valorBase.add(BigDecimal.valueOf(8)));
        response.setValorCupom10(valorBase.add(BigDecimal.valueOf(10)));

        return response;
    }

    public SimulacaoReversaResponseDTO simularReversa(SimulacaoReversaRequestDTO dto) {
        Item item = itemRepository.findById(dto.getItemId())
                .orElseThrow(() -> new ItemNaoEncontradoException(dto.getItemId()));

        BigDecimal cmv = item.getCmv();
        BigDecimal frete = dto.getFrete();
        BigDecimal precoVenda = dto.getPrecoVenda();

        BigDecimal cf = cmv.add(frete);

        BigDecimal percentualTotal = dto.getImposto()
                .add(dto.getTaxaFranquia())
                .add(dto.getCustoFixo())
                .add(dto.getTaxaTransacao())
                .add(dto.getTaxaIfood())
                .add(dto.getTaxaRepasse());

        if (percentualTotal.compareTo(BigDecimal.valueOf(100)) >= 0) {
            throw new PercentualInvalidoException();
        }

        BigDecimal percentualDecimal = percentualTotal.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal coeficienteC = BigDecimal.ONE.subtract(percentualDecimal);

        BigDecimal custoPercentualReais = precoVenda
                .multiply(percentualDecimal)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal lucroReais = precoVenda
                .subtract(custoPercentualReais)
                .subtract(cf)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal lucroPercentual = lucroReais
                .divide(precoVenda, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        SimulacaoReversaResponseDTO response = new SimulacaoReversaResponseDTO();
        response.setItemId(item.getId());
        response.setNomeItem(item.getNomeItem());
        response.setLojaId(item.getLoja().getId());
        response.setNomeLoja(item.getLoja().getNome());
        response.setCmv(cmv);
        response.setFrete(frete);
        response.setCf(cf);
        response.setPrecoVenda(precoVenda);
        response.setPercentualTotal(percentualTotal);
        response.setCoeficienteC(coeficienteC);
        response.setCustoPercentualReais(custoPercentualReais);
        response.setLucroReais(lucroReais);
        response.setLucroPercentual(lucroPercentual);

        return response;
    }

    public Simulacao99ResponseDTO simular99(Simulacao99RequestDTO dto) {
        // Busca o item no banco
        Item item = itemRepository.findById(dto.getItemId())
                .orElseThrow(() -> new ItemNaoEncontradoException(dto.getItemId()));

        // CMV vem do item cadastrado
        BigDecimal cmv = item.getCmv();

        // Custo logístico vem da faixa de distância escolhida
        BigDecimal custoLogistico = dto.getFaixaDistancia().getCustoLogistico();

        // Soma de todos os percentuais
        BigDecimal percentualTotal = dto.getTaxa99()
                .add(dto.getImposto())
                .add(dto.getCustoFixo())
                .add(dto.getTaxaFranquia())
                .add(dto.getTaxaTransacao())
                .add(dto.getTaxaAntecipacao())
                .add(dto.getLucro());

        // Não podemos ter 100% ou mais de custos
        if (percentualTotal.compareTo(BigDecimal.valueOf(100)) >= 0) {
                throw new PercentualInvalidoException();
        }

        // Exemplo:
        // 50,20% -> 0,502
        BigDecimal percentualDecimal = percentualTotal.divide(
                BigDecimal.valueOf(100),
                8,
                RoundingMode.HALF_UP
        );

        // k = 1 - soma dos percentuais
        // Exemplo: 1 - 0,502 = 0,498
        BigDecimal coeficiente = BigDecimal.ONE.subtract(percentualDecimal);

        /*
        * VALOR NORMAL DO PRATO
        *
        * (CMV + custo logístico) / k
        */
        BigDecimal custoBase = cmv.add(custoLogistico);

        BigDecimal valorPratoSemArredondar = custoBase.divide(
                coeficiente,
                10,
                RoundingMode.HALF_UP
        );

        BigDecimal valorPrato = valorPratoSemArredondar.setScale(
                2,
                RoundingMode.HALF_UP
        );

        /*
        * FRETE GRÁTIS
        *
        * (CMV + custo logístico + 5) / k
        */
        BigDecimal custoBaseFreteGratis = custoBase.add(new BigDecimal("5.00"));

        BigDecimal valorFreteGratisSemArredondar = custoBaseFreteGratis.divide(
                coeficiente,
                10,
                RoundingMode.HALF_UP
        );

        BigDecimal valorFreteGratis = valorFreteGratisSemArredondar.setScale(
                2,
                RoundingMode.HALF_UP
        );

        /*
        * PROMOÇÕES
        *
        * Importante:
        * usamos valorFreteGratisSemArredondar.
        *
        * Não usamos R$ 44,44 já arredondado,
        * pois isso poderia alterar o resultado final.
        */

        BigDecimal valor20Off = valorFreteGratisSemArredondar
                .divide(new BigDecimal("0.80"), 10, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal valor30Off = valorFreteGratisSemArredondar
                .divide(new BigDecimal("0.70"), 10, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal valor40Off = valorFreteGratisSemArredondar
                .divide(new BigDecimal("0.60"), 10, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal valor50Off = valorFreteGratisSemArredondar
                .divide(new BigDecimal("0.50"), 10, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal valor60Off = valorFreteGratisSemArredondar
                .divide(new BigDecimal("0.40"), 10, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);
        
        /*
        * CUPONS COM COPARTICIPAÇÃO DA 99FOOD
        *
        * 30% OFF:
        * Cliente paga 70%
        * 99 participa com 15% dos 30% de desconto = 4,5%
        * Loja recebe 74,5%
        *
        * 40% OFF:
        * Cliente paga 60%
        * 99 participa com 20% dos 40% de desconto = 8%
        * Loja recebe 68%
        *
        * 50% OFF:
        * Cliente paga 50%
        * 99 participa com 25% dos 50% de desconto = 12,5%
        * Loja recebe 62,5%
        */

        BigDecimal valor30OffCoparticipacao = valorFreteGratisSemArredondar
                .divide(new BigDecimal("0.745"), 10, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal valor40OffCoparticipacao = valorFreteGratisSemArredondar
                .divide(new BigDecimal("0.68"), 10, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal valor50OffCoparticipacao = valorFreteGratisSemArredondar
                .divide(new BigDecimal("0.625"), 10, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);

        // Monta a resposta
        Simulacao99ResponseDTO response = new Simulacao99ResponseDTO();

        response.setItemId(item.getId());
        response.setNomeItem(item.getNomeItem());

        response.setLojaId(item.getLoja().getId());
        response.setNomeLoja(item.getLoja().getNome());

        response.setCmv(cmv);

        response.setFaixaDistancia(dto.getFaixaDistancia());
        response.setCustoLogistico(custoLogistico);

        response.setPercentualTotal(percentualTotal);
        response.setCoeficiente(coeficiente);

        response.setValorPrato(valorPrato);
        response.setValorFreteGratis(valorFreteGratis);

        response.setValor20Off(valor20Off);
        response.setValor30Off(valor30Off);
        response.setValor40Off(valor40Off);
        response.setValor50Off(valor50Off);
        response.setValor60Off(valor60Off);

        response.setValor30OffCoparticipacao(valor30OffCoparticipacao);
        response.setValor40OffCoparticipacao(valor40OffCoparticipacao);
        response.setValor50OffCoparticipacao(valor50OffCoparticipacao);

        return response;
        }

}
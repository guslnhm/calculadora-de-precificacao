package com.precificacao.precificacao.service;

import com.precificacao.precificacao.dto.SimulacaoRequestDTO;
import com.precificacao.precificacao.dto.SimulacaoResponseDTO;
import com.precificacao.precificacao.entity.Item;
import com.precificacao.precificacao.repository.ItemRepository;
import org.springframework.stereotype.Service;
import com.precificacao.precificacao.exception.*;
import com.precificacao.precificacao.dto.SimulacaoReversaRequestDTO;
import com.precificacao.precificacao.dto.SimulacaoReversaResponseDTO;

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

}
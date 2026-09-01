package com.precificacao.precificacao.service;

import com.precificacao.precificacao.dto.LojaPlataformasRequestDTO;
import com.precificacao.precificacao.dto.LojaPlataformasResponseDTO;
import com.precificacao.precificacao.entity.Loja;
import com.precificacao.precificacao.entity.LojaPlataforma;
import com.precificacao.precificacao.enums.Plataforma;
import com.precificacao.precificacao.repository.LojaPlataformaRepository;
import com.precificacao.precificacao.repository.LojaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LojaPlataformaService {

    private final LojaPlataformaRepository lojaPlataformaRepository;
    private final LojaRepository lojaRepository;

    public LojaPlataformaService(
            LojaPlataformaRepository lojaPlataformaRepository,
            LojaRepository lojaRepository
    ) {
        this.lojaPlataformaRepository = lojaPlataformaRepository;
        this.lojaRepository = lojaRepository;
    }

    public LojaPlataformasResponseDTO buscar(Long lojaId) {

        Loja loja = lojaRepository.findById(lojaId)
                .orElseThrow(() -> new RuntimeException("Loja não encontrada"));

        Set<Plataforma> plataformas =
                lojaPlataformaRepository
                        .findByLojaIdAndAtivoTrue(lojaId)
                        .stream()
                        .map(LojaPlataforma::getPlataforma)
                        .collect(Collectors.toCollection(
                                () -> EnumSet.noneOf(Plataforma.class)
                        ));

        return montarResponse(loja, plataformas);
    }

    @Transactional
    public LojaPlataformasResponseDTO atualizar(
            Long lojaId,
            LojaPlataformasRequestDTO dto
    ) {

        Loja loja = lojaRepository.findById(lojaId)
                .orElseThrow(() -> new RuntimeException("Loja não encontrada"));

        Set<Plataforma> plataformasSelecionadas =
                EnumSet.copyOf(dto.getPlataformas());

        List<LojaPlataforma> vinculosExistentes =
                lojaPlataformaRepository.findByLojaId(lojaId);

        /*
         * Primeiro atualizamos tudo que já existe.
         *
         * Se a plataforma estiver selecionada:
         * ativo = true
         *
         * Se não estiver:
         * ativo = false
         */
        for (LojaPlataforma vinculo : vinculosExistentes) {
            boolean deveFicarAtivo =
                    plataformasSelecionadas.contains(
                            vinculo.getPlataforma()
                    );

            vinculo.setAtivo(deveFicarAtivo);

            lojaPlataformaRepository.save(vinculo);
        }

        /*
         * Agora verificamos se alguma plataforma selecionada
         * nunca existiu para esta loja.
         *
         * Nesse caso, criamos o vínculo.
         */
        for (Plataforma plataforma : plataformasSelecionadas) {

            boolean jaExiste = vinculosExistentes
                    .stream()
                    .anyMatch(vinculo ->
                            vinculo.getPlataforma() == plataforma
                    );

            if (!jaExiste) {
                LojaPlataforma novoVinculo =
                        new LojaPlataforma();

                novoVinculo.setLoja(loja);
                novoVinculo.setPlataforma(plataforma);
                novoVinculo.setAtivo(true);

                lojaPlataformaRepository.save(novoVinculo);
            }
        }

        return buscar(lojaId);
    }

    private LojaPlataformasResponseDTO montarResponse(
            Loja loja,
            Set<Plataforma> plataformas
    ) {

        LojaPlataformasResponseDTO dto =
                new LojaPlataformasResponseDTO();

        dto.setLojaId(loja.getId());
        dto.setNomeLoja(loja.getNome());
        dto.setPlataformas(plataformas);

        return dto;
    }
}
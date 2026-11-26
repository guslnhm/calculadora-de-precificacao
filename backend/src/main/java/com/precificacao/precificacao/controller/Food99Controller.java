package com.precificacao.precificacao.controller;

import com.precificacao.precificacao.acesso.AcessoException;
import com.precificacao.precificacao.food99.Food99Exception;
import com.precificacao.precificacao.food99.Food99Scheduler;
import com.precificacao.precificacao.food99.Food99Service;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** 99Food (só equipe; o filtro barra mentorados fora de /painel/carteira e /painel/loja). */
@RestController
@RequestMapping("/painel/99food")
public class Food99Controller {

    private final Food99Service service;
    private final Food99Scheduler agenda;

    public Food99Controller(Food99Service service, Food99Scheduler agenda) {
        this.service = service;
        this.agenda = agenda;
    }

    @GetMapping("/situacao")
    public Map<String, Object> situacao() {
        return service.situacao();
    }

    /** Link (vale 7 dias) para o super-admin da loja na 99 autorizar o nosso app. */
    @PostMapping("/link-autorizacao")
    public Map<String, Object> link() {
        exigirConfigurado();
        try {
            return Map.of("url", service.linkAutorizacao());
        } catch (Food99Exception e) {
            throw new AcessoException(HttpStatus.BAD_GATEWAY, e.getMessage());
        }
    }

    /** Coleta em segundo plano; sem datas, os últimos dias. */
    @PostMapping("/coletar")
    public Map<String, Object> coletar(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        exigirConfigurado();
        if ((de == null) != (ate == null)) {
            throw AcessoException.invalido("Informe as duas datas (de e ate) ou nenhuma");
        }
        if (de != null && (de.isAfter(ate) || ChronoUnit.DAYS.between(de, ate) > 120)) {
            throw AcessoException.invalido("Período inválido (máximo 120 dias)");
        }
        boolean ok = agenda.disparar(de == null ? LocalDate.MIN : de, ate);
        return Map.of("iniciada", ok, "mensagem", ok ? "Coleta iniciada" : "Já tem uma coleta rodando");
    }

    @GetMapping("/dias")
    public List<Food99Service.DiaReais> dias(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        if (de.isAfter(ate) || ChronoUnit.DAYS.between(de, ate) > 400) {
            throw AcessoException.invalido("Período inválido (máximo 400 dias)");
        }
        return service.dias(de, ate);
    }

    private void exigirConfigurado() {
        if (!service.configurado()) {
            throw AcessoException.invalido("A 99Food ainda não está configurada: falta FOOD99_APP_ID e FOOD99_APP_SECRET no Railway");
        }
    }
}

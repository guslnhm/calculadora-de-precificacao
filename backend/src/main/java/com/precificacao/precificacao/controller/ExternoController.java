package com.precificacao.precificacao.controller;

import com.precificacao.precificacao.acesso.AcessoException;
import com.precificacao.precificacao.externo.AcessoExternoRepository.Linha;
import com.precificacao.precificacao.externo.AcessoExternoService;
import com.precificacao.precificacao.externo.AcessoExternoService.Venda;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Números diários para sistemas de fora (BI de metas etc.). Não usa o login do site: vale a chave do acesso,
 * em "Authorization: Bearer rzq_…", no cabeçalho "X-Chave" ou em ?chave= (para BIs que só leem um link).
 * Só devolve as lojas liberadas para a chave.
 */
@RestController
@RequestMapping("/externo")
public class ExternoController {

    static final String COLUNAS = "data,merchant_id,loja,pedidos,cancelados,faturamento,faturamento_cancelado,"
            + "ticket_medio,novos_clientes,atualizado_em,id_loja_portal,valor_itens,taxa_entrega,desconto_loja";

    private final AcessoExternoService service;

    public ExternoController(AcessoExternoService service) {
        this.service = service;
    }

    @GetMapping("/lojas")
    public ResponseEntity<?> lojas(HttpServletRequest http, @RequestParam(required = false) String chave) {
        Linha a = acesso(http, chave);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("acesso", a.nome(), "lojas", service.lojasDo(a)));
    }

    /**
     * Pedidos = não cancelados. faturamento = GMV do iFood dos não cancelados = itens + frete pago pelo cliente
     * - descontos bancados pela loja. valor_itens = itens sem frete e antes dos descontos da loja.
     * id_loja_portal = id curto da loja, o mesmo dos relatórios do Portal.
     * formato: json (padrão), csv (vírgula e ponto decimal) ou csv-excel (ponto e vírgula e vírgula decimal).
     */
    @GetMapping("/vendas")
    public ResponseEntity<?> vendas(HttpServletRequest http,
                                    @RequestParam(required = false) String chave,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
                                    @RequestParam(required = false) String loja,
                                    @RequestParam(defaultValue = "json") String formato) {
        Linha a = acesso(http, chave);
        List<Venda> v = service.vendas(a, de, ate, loja);
        String f = formato.strip().toLowerCase();
        if (f.equals("csv") || f.equals("csv-excel")) {
            boolean excel = f.equals("csv-excel");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .header("Content-Disposition", "inline; filename=\"vendas.csv\"")
                    .body(csv(v, excel));
        }
        if (!f.equals("json")) {
            throw AcessoException.invalido("formato deve ser json, csv ou csv-excel");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("vendas", v));
    }

    static String csv(List<Venda> vendas, boolean excel) {
        String sep = excel ? ";" : ",";
        StringBuilder sb = new StringBuilder(excel ? "﻿" : "");
        sb.append(excel ? COLUNAS.replace(',', ';') : COLUNAS).append("\r\n");
        for (Venda x : vendas) {
            sb.append(x.data()).append(sep)
                    .append(x.merchantId()).append(sep)
                    .append(texto(x.loja())).append(sep)
                    .append(x.pedidos()).append(sep)
                    .append(x.cancelados()).append(sep)
                    .append(numero(x.faturamento(), excel)).append(sep)
                    .append(numero(x.faturamentoCancelado(), excel)).append(sep)
                    .append(numero(x.ticketMedio(), excel)).append(sep)
                    .append(x.novosClientes() == null ? "" : x.novosClientes()).append(sep)
                    .append(x.atualizadoEm() == null ? "" : x.atualizadoEm().toString()).append(sep)
                    .append(x.idLojaPortal() == null ? "" : x.idLojaPortal()).append(sep)
                    .append(numero(x.valorItens(), excel)).append(sep)
                    .append(numero(x.taxaEntrega(), excel)).append(sep)
                    .append(numero(x.descontoLoja(), excel))
                    .append("\r\n");
        }
        return sb.toString();
    }

    private static String numero(BigDecimal v, boolean excel) {
        if (v == null) {
            return "";
        }
        String s = v.toPlainString();
        return excel ? s.replace('.', ',') : s;
    }

    private static String texto(String s) {
        if (s == null) {
            return "";
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private Linha acesso(HttpServletRequest http, String chaveParam) {
        String chave = chaveParam;
        String auth = http.getHeader("Authorization");
        if (auth != null && !auth.isBlank()) {
            chave = auth.regionMatches(true, 0, "Bearer ", 0, 7) ? auth.substring(7) : auth;
        } else if (http.getHeader("X-Chave") != null) {
            chave = http.getHeader("X-Chave");
        }
        return service.autenticar(chave).orElseThrow(() ->
                new AcessoException(HttpStatus.UNAUTHORIZED, "Chave inválida ou desativada"));
    }
}

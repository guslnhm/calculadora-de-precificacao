package com.precificacao.precificacao.cardapio;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Monitor de itens pausados (seção "monitor.itens"). Só roda com o monitor de lojas ligado (MONITOR_ATIVO=true).
 *
 * @param intervaloMinutos     de quanto em quanto tempo relê o cardápio de cada loja
 * @param complementos         monitora também os complementos (bebida do combo, adicionais…)
 * @param alertarNaoVendavel   avisa item "disponível" que o iFood não deixa vender (fora do turno, complemento obrigatório vazio…)
 * @param somenteComLojaAberta com a loja fechada só registra; avisa quando ela abrir com a pausa ainda ativa
 * @param ignorarCategorias    nomes exatos de categorias que não devem ser monitoradas
 */
@ConfigurationProperties(prefix = "monitor.itens")
public record ItensProperties(
        @DefaultValue("true") boolean ativo,
        @DefaultValue("10") int intervaloMinutos,
        @DefaultValue("true") boolean complementos,
        @DefaultValue("true") boolean alertarNaoVendavel,
        @DefaultValue("true") boolean somenteComLojaAberta,
        @DefaultValue("3") int workers,
        @DefaultValue("") List<String> ignorarCategorias
) {
    public Set<String> categoriasIgnoradas() {
        Set<String> s = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (ignorarCategorias != null) {
            ignorarCategorias.stream().map(String::strip).filter(x -> !x.isEmpty()).forEach(s::add);
        }
        return s;
    }
}

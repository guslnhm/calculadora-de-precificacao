package com.precificacao.precificacao.monitor;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuração do monitor de lojas (seção "monitor" do application.yaml).
 *
 * @param ativo             liga o ciclo automático (MONITOR_ATIVO=true)
 * @param enviarWhatsapp    false = "modo sombra": calcula e registra as mensagens, mas não envia
 * @param ciclosConfirmacao ciclos seguidos com problema antes de alertar (evita alarme falso)
 * @param estadosAlerta     estados que geram alerta (ERROR; acrescente WARNING se quiser raio reduzido etc.)
 * @param alertarPausas     loja pausada (o iFood marca como CLOSED + pausa ativa) também gera alerta
 * @param lembreteMinutos   reenvia "ainda fechada" a cada N minutos; 0 desliga
 * @param resumoDiario      horário do resumo do dia anterior (HH:mm); vazio desliga
 */
@ConfigurationProperties(prefix = "monitor")
public record MonitorProperties(
        @DefaultValue("false") boolean ativo,
        @DefaultValue("false") boolean enviarWhatsapp,
        @DefaultValue("30") int intervaloSegundos,
        @DefaultValue("2") int ciclosConfirmacao,
        @DefaultValue("ERROR") List<String> estadosAlerta,
        @DefaultValue("true") boolean alertarPausas,
        @DefaultValue("30") int lembreteMinutos,
        @DefaultValue("true") boolean avisarRecuperacao,
        @DefaultValue("") List<String> ignorarOperacoes,
        @DefaultValue("8") int workers,
        @DefaultValue("60") int atualizarLojasMinutos,
        @DefaultValue("10") int falhasParaAvisar,
        @DefaultValue("08:00") String resumoDiario
) {

    /** Estados que abrem incidente (em maiúsculas), incluindo PAUSED quando alertarPausas. */
    public Set<String> estadosQueAlertam() {
        Set<String> s = estadosAlerta.stream().filter(e -> !e.isBlank())
                .map(String::toUpperCase).collect(Collectors.toCollection(HashSet::new));
        if (alertarPausas) {
            s.add("PAUSED");
        }
        return s;
    }

    public Set<String> operacoesIgnoradas() {
        return ignorarOperacoes.stream().filter(e -> !e.isBlank())
                .map(String::toUpperCase).collect(Collectors.toSet());
    }
}

package com.precificacao.precificacao.monitor;

import com.precificacao.precificacao.ifood.merchant.StatusOperacao;
import com.precificacao.precificacao.ifood.merchant.TipoMotivo;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Tipos usados pelo monitor de lojas. */
public final class MonitorModelos {

    private MonitorModelos() {
    }

    /** Operação/canal de uma loja. */
    public record Chave(String merchantId, String operacao, String canal) {
    }

    /** Loja que autorizou o aplicativo. */
    public record LojaMerchant(String merchantId, String nome, String razaoSocial) {
    }

    /** Pausa ativa ou futura (horários como o iFood devolve: hora local, às vezes com fuso). */
    public record Pausa(String id, String descricao, String inicio, String fim) {
    }

    /** Motivo gravado no banco (JSON). */
    public record MotivoSalvo(String codigo, String estado, String texto, String tipo) {

        public static MotivoSalvo de(StatusOperacao.Motivo m) {
            return new MotivoSalvo(m.codigo(), m.estado(), m.texto(), m.tipo().name());
        }

        public TipoMotivo tipoMotivo() {
            try {
                return TipoMotivo.valueOf(tipo);
            } catch (RuntimeException e) {
                return TipoMotivo.deCodigo(codigo);
            }
        }
    }

    public static List<MotivoSalvo> motivosDe(StatusOperacao st) {
        return st.motivos().stream().map(MotivoSalvo::de).collect(Collectors.toCollection(ArrayList::new));
    }

    /** Última situação conhecida de uma operação. */
    public record StatusSalvo(String estado, OffsetDateTime desde, String assinatura) {
    }

    /** Período em que uma operação ficou em estado de alerta. */
    public static class Incidente {
        public long id;
        public String merchantId;
        public String operacao;
        public String canal;
        public String estado;
        public List<MotivoSalvo> motivos = new ArrayList<>();
        public String mensagem;
        public Boolean reabrivel;
        public OffsetDateTime inicio;
        public OffsetDateTime confirmadoEm;
        public OffsetDateTime fim;
        public String estadoFinal;
        public OffsetDateTime ultimoLembreteEm;

        public Chave chave() {
            return new Chave(merchantId, operacao, canal);
        }
    }

    /** Linha do relatório de incidentes. */
    public record IncidenteRelatorio(
            long id, String merchantId, String loja, String operacao, String canal, String estado,
            List<MotivoSalvo> motivos, String mensagem, Boolean reabrivel,
            OffsetDateTime inicio, OffsetDateTime fim, String estadoFinal, double minutos, boolean emAberto) {
    }

    /** Grupo de WhatsApp e as lojas que ele acompanha. */
    public record Grupo(long id, String nome, String chatId, boolean todasLojas, boolean admin,
                        boolean avisarRetorno, boolean ativo, Set<String> lojas) {

        public boolean acompanha(String merchantId) {
            return todasLojas || lojas.contains(merchantId);
        }
    }

    public enum TipoEvento { ABERTO, MUDOU, LEMBRETE, NORMALIZADO }

    /** Algo que vira uma linha de mensagem no WhatsApp. */
    public record Evento(TipoEvento tipo, String merchantId, String nomeLoja, StatusOperacao status,
                         Incidente incidente, OffsetDateTime agora, List<Pausa> pausas, String estadoFinal) {
    }
}

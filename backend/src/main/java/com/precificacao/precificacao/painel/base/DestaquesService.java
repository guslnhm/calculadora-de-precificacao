package com.precificacao.precificacao.painel.base;

import com.precificacao.precificacao.ifood.IfoodProperties;
import com.precificacao.precificacao.monitor.EnvioWhatsapp;
import com.precificacao.precificacao.monitor.GrupoWhatsappRepository;
import com.precificacao.precificacao.monitor.MonitorModelos.Grupo;
import com.precificacao.precificacao.monitor.MonitorProperties;
import com.precificacao.precificacao.monitor.MonitorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Destaques do dia anterior (custo do iFood acima do limite e recordes) para o(s) grupo(s) admin.
 * Sai uma vez por dia, a partir do horário do resumo diário, assim que o relatório de pedidos de ontem estiver na
 * base (pelo menos metade dos pedidos do mesmo dia da semana anterior). Se às 15h o relatório ainda não
 * chegou, avisa uma vez o grupo admin.
 */
@Service
public class DestaquesService {

    private static final Logger log = LoggerFactory.getLogger(DestaquesService.class);
    static final LocalTime LEMBRETE = LocalTime.of(15, 0);
    static final Duration INTERVALO_CHECAGEM = Duration.ofMinutes(5);

    private final BaseHistoricaService base;
    private final GrupoWhatsappRepository grupos;
    private final EnvioWhatsapp envio;
    private final MonitorRepository monitorRepo;
    private final MonitorProperties monitorCfg;
    private final Clock clock;
    private final ZoneId zona;
    private OffsetDateTime ultimaChecagem;

    public DestaquesService(BaseHistoricaService base, GrupoWhatsappRepository grupos, EnvioWhatsapp envio,
                            MonitorRepository monitorRepo, MonitorProperties monitorCfg, Clock clock, IfoodProperties ifood) {
        this.base = base;
        this.grupos = grupos;
        this.envio = envio;
        this.monitorRepo = monitorRepo;
        this.monitorCfg = monitorCfg;
        this.clock = clock;
        this.zona = ifood.zona();
    }

    /** Chamado a cada ciclo do monitor; checa no máximo a cada 5 min. */
    public synchronized void ciclo() {
        if (!base.configurada() || monitorCfg.resumoDiario() == null || monitorCfg.resumoDiario().isBlank()) {
            return;
        }
        OffsetDateTime agora = OffsetDateTime.now(clock);
        if (ultimaChecagem != null && Duration.between(ultimaChecagem, agora).compareTo(INTERVALO_CHECAGEM) < 0) {
            return;
        }
        ultimaChecagem = agora;
        LocalDate hoje = agora.atZoneSameInstant(zona).toLocalDate();
        OffsetDateTime inicio = hoje.atTime(LocalTime.parse(monitorCfg.resumoDiario())).atZone(zona).toOffsetDateTime();
        OffsetDateTime inicioHoje = hoje.atStartOfDay(zona).toOffsetDateTime();
        if (agora.isBefore(inicio) || enviadoDesde("destaques", inicioHoje)) {
            return;
        }
        LocalDate ontem = hoje.minusDays(1);
        if (relatorioChegou(ontem)) {
            enviar(ontem);
        } else if (!agora.isBefore(hoje.atTime(LEMBRETE).atZone(zona).toOffsetDateTime())
                && !enviadoDesde("destaques-lembrete", inicioHoje)) {
            paraAdmins("destaques-lembrete", "📎 O relatório de pedidos de ontem (" + ontem.format(DateTimeFormatter.ofPattern("dd/MM"))
                    + ") ainda não foi enviado ao site. Sem ele não dá para calcular o custo do iFood nem os recordes do dia.");
        }
    }

    private boolean enviadoDesde(String tipo, OffsetDateTime desde) {
        OffsetDateTime u = monitorRepo.ultimoEnvio(tipo);
        return u != null && !u.isBefore(desde);
    }

    boolean relatorioChegou(LocalDate dia) {
        return base.naBase(c -> {
            int doDia = Destaques.pedidosDoDia(c, dia);
            int semanaPassada = Destaques.pedidosDoDia(c, dia.minusDays(7));
            return doDia >= 10 && doDia * 2 >= semanaPassada;
        });
    }

    /** Texto dos destaques do dia (sem enviar). */
    public String texto(LocalDate dia) {
        return base.naBase(c -> {
            String t = Destaques.mensagem(dia, Destaques.custos(c, dia), Destaques.recordes(c, dia), Destaques.LIMITE_CUSTO);
            return t != null ? t : "📈 *Destaques de ontem — " + dia.format(DateTimeFormatter.ofPattern("dd/MM"))
                    + "*\nNenhuma loja com custo do iFood acima de 35% e nenhum recorde.";
        });
    }

    /** Envia os destaques do dia ao(s) grupo(s) admin. */
    public synchronized Map<String, String> enviar(LocalDate dia) {
        String t = texto(dia);
        return paraAdmins("destaques", t);
    }

    private Map<String, String> paraAdmins(String tipo, String texto) {
        List<Grupo> admins = grupos.listar(true).stream().filter(Grupo::admin).toList();
        Map<String, String> out = new LinkedHashMap<>();
        if (admins.isEmpty()) {
            log.warn("[destaques sem grupo admin] {}", texto);
            return out;
        }
        for (Grupo g : admins) {
            out.put(g.nome(), envio.enviar(tipo, g, texto));
        }
        return out;
    }
}

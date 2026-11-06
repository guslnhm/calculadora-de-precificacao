package com.precificacao.precificacao.lojaifood;

import com.fasterxml.jackson.databind.JsonNode;
import com.precificacao.precificacao.ifood.IfoodException;
import com.precificacao.precificacao.ifood.merchant.IfoodMerchantClient;
import com.precificacao.precificacao.ifood.merchant.StatusOperacao;
import com.precificacao.precificacao.ifood.merchant.Turno;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A loja no iFood pelo site: informações, disponibilidade, pausas (interrupções) e horário de funcionamento.
 * Tudo direto na API Merchant, ao vivo.
 */
@Service
public class LojaIfoodService {

    public static final List<String> DIAS = List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");

    public record Loja(String id, String nome, String razaoSocial) {
    }

    public record Pausa(String id, String descricao, String inicio, String fim) {
    }

    public record Faixa(String inicio, String fim) {
    }

    /** Leitura/escrita na API (trocável nos testes). */
    public interface Api {
        List<JsonNode> lojas();

        JsonNode detalhes(String merchantId);

        List<StatusOperacao> status(String merchantId);

        JsonNode pausas(String merchantId);

        JsonNode pausar(String merchantId, int minutos, String descricao);

        void removerPausa(String merchantId, String pausaId);

        JsonNode horarios(String merchantId);

        void definirHorarios(String merchantId, List<Turno> turnos);
    }

    private final Api api;
    private final long esperaMs;

    @org.springframework.beans.factory.annotation.Autowired
    public LojaIfoodService(IfoodMerchantClient merchant) {
        this(new Api() {
            @Override
            public List<JsonNode> lojas() {
                return merchant.listarLojas();
            }

            @Override
            public JsonNode detalhes(String merchantId) {
                return merchant.detalhesLoja(merchantId);
            }

            @Override
            public List<StatusOperacao> status(String merchantId) {
                return merchant.status(merchantId);
            }

            @Override
            public JsonNode pausas(String merchantId) {
                return merchant.listarPausas(merchantId);
            }

            @Override
            public JsonNode pausar(String merchantId, int minutos, String descricao) {
                return merchant.pausarPor(merchantId, minutos, descricao);
            }

            @Override
            public void removerPausa(String merchantId, String pausaId) {
                merchant.removerPausa(merchantId, pausaId);
            }

            @Override
            public JsonNode horarios(String merchantId) {
                return merchant.horarios(merchantId);
            }

            @Override
            public void definirHorarios(String merchantId, List<Turno> turnos) {
                merchant.definirHorarios(merchantId, turnos);
            }
        }, 2500);
    }

    public LojaIfoodService(Api api, long esperaMs) {
        this.api = api;
        this.esperaMs = esperaMs;
    }

    // ------------------------------------------------------------------ informações

    /** Lojas que autorizaram o aplicativo (GET /merchants). */
    public List<Loja> lojas() {
        List<Loja> out = new ArrayList<>();
        for (JsonNode m : api.lojas()) {
            out.add(new Loja(m.path("id").asText(), m.path("name").asText(), texto(m, "corporateName")));
        }
        out.sort((a, b) -> a.nome().compareToIgnoreCase(b.nome()));
        return out;
    }

    /** Detalhes completos (GET /merchants/{id}), disponibilidade (GET /status) e pausas ativas. */
    public Map<String, Object> informacoes(String merchantId) {
        JsonNode d = api.detalhes(merchantId);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", texto(d, "id") != null ? texto(d, "id") : merchantId);
        m.put("nome", texto(d, "name"));
        m.put("razaoSocial", texto(d, "corporateName"));
        m.put("descricao", texto(d, "description"));
        m.put("cnpj", texto(d, "cnpj") != null ? texto(d, "cnpj") : texto(d, "documentNumber"));
        m.put("ticketMedio", d.path("averageTicket").isNumber() ? d.path("averageTicket").decimalValue() : null);
        m.put("tipo", texto(d, "merchantType"));
        m.put("criadaEm", texto(d, "createdAt"));
        JsonNode a = d.path("address");
        if (a.isObject()) {
            Map<String, Object> e = new LinkedHashMap<>();
            for (String[] c : new String[][]{{"rua", "street"}, {"numero", "number"}, {"complemento", "complement"},
                    {"bairro", "district"}, {"cidade", "city"}, {"estado", "state"}, {"cep", "postalCode"},
                    {"pais", "country"}, {"latitude", "latitude"}, {"longitude", "longitude"}}) {
                if (a.hasNonNull(c[1])) {
                    e.put(c[0], a.get(c[1]).isNumber() ? a.get(c[1]).numberValue() : a.get(c[1]).asText());
                }
            }
            m.put("endereco", e);
        }
        List<Map<String, Object>> ops = new ArrayList<>();
        for (JsonNode op : d.path("operations")) {
            List<String> canais = new ArrayList<>();
            for (JsonNode c : op.path("salesChannels")) {
                canais.add(c.path("name").asText() + (c.path("enabled").asBoolean(true) ? "" : " (desligado)"));
            }
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("nome", op.path("name").asText());
            o.put("canais", canais);
            ops.add(o);
        }
        m.put("operacoes", ops);
        m.put("status", api.status(merchantId));
        m.put("pausas", pausas(merchantId));
        m.put("bruto", d);
        return m;
    }

    // ------------------------------------------------------------------ pausas

    public List<Pausa> pausas(String merchantId) {
        List<Pausa> out = new ArrayList<>();
        for (JsonNode p : api.pausas(merchantId)) {
            out.add(new Pausa(texto(p, "id"), texto(p, "description"), texto(p, "start"), texto(p, "end")));
        }
        return out;
    }

    public Map<String, Object> pausar(String merchantId, int minutos, String descricao) {
        if (minutos < 1 || minutos > 24 * 60) {
            throw new IllegalArgumentException("Duração inválida: use de 1 minuto a 24 horas");
        }
        String d = descricao == null || descricao.isBlank() ? "Pausa pelo sistema" : descricao.strip();
        JsonNode criada = api.pausar(merchantId, minutos, d);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("criada", new Pausa(texto(criada, "id"), texto(criada, "description"), texto(criada, "start"), texto(criada, "end")));
        dormir();
        m.put("pausas", pausas(merchantId)); // a lista do iFood pode levar alguns segundos para mostrar
        return m;
    }

    /**
     * Remove uma pausa. Sem ID, remove todas as ativas: pela lista e, se ela ainda não mostrou
     * (o iFood processa de forma assíncrona), pelo ID que aparece no status da loja (reopenable.identifier).
     */
    public List<String> removerPausa(String merchantId, String pausaId) {
        Set<String> ids = new LinkedHashSet<>();
        if (pausaId != null && !pausaId.isBlank()) {
            ids.add(pausaId);
        } else {
            pausas(merchantId).forEach(p -> {
                if (p.id() != null) {
                    ids.add(p.id());
                }
            });
            if (ids.isEmpty()) {
                for (StatusOperacao st : api.status(merchantId)) {
                    JsonNode r = st.bruto() == null ? null : st.bruto().path("reopenable");
                    if (r != null && r.hasNonNull("identifier") && r.path("reopenable").asBoolean(true)) {
                        String tipo = r.path("type").asText("UNAVAILABILITY").toUpperCase();
                        if (tipo.contains("UNAVAILAB")) {
                            ids.add(r.get("identifier").asText());
                        }
                    }
                }
            }
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma pausa ativa encontrada");
        }
        for (String id : ids) {
            api.removerPausa(merchantId, id);
        }
        return new ArrayList<>(ids);
    }

    // ------------------------------------------------------------------ horários

    /** Horário de cada dia (segunda a domingo); dia sem faixa = fechado. */
    public Map<String, List<Faixa>> horarios(String merchantId) {
        Map<String, List<Faixa>> out = new LinkedHashMap<>();
        DIAS.forEach(d -> out.put(d, new ArrayList<>()));
        JsonNode r = api.horarios(merchantId);
        JsonNode shifts = r.has("shifts") ? r.get("shifts") : r;
        for (JsonNode s : shifts) {
            try {
                Turno t = new Turno(s.path("dayOfWeek").asText(), s.path("start").asText("00:00:00"), s.path("duration").asInt());
                out.computeIfAbsent(t.dayOfWeek(), k -> new ArrayList<>())
                        .add(new Faixa(hhmm(t.inicio().toString()), hhmm(t.fim().toString())));
            } catch (RuntimeException e) {
                // turno estranho vindo do iFood: ignora na tela
            }
        }
        out.values().forEach(l -> l.sort((a, b) -> a.inicio().compareTo(b.inicio())));
        return out;
    }

    /** Substitui TODOS os horários (dia sem faixa fica fechado) e devolve o que o iFood passou a mostrar. */
    public Map<String, List<Faixa>> definirHorarios(String merchantId, Map<String, List<Faixa>> dias) {
        List<Turno> turnos = new ArrayList<>();
        for (var e : dias.entrySet()) {
            List<Faixa> faixas = e.getValue() == null ? List.of() : e.getValue();
            if (faixas.isEmpty()) {
                continue;
            }
            StringBuilder spec = new StringBuilder();
            for (Faixa f : faixas) {
                if (spec.length() > 0) {
                    spec.append(',');
                }
                spec.append(f.inicio()).append('-').append(f.fim());
            }
            turnos.addAll(Turno.doDia(e.getKey(), spec.toString()));
        }
        if (turnos.isEmpty()) {
            throw new IllegalArgumentException("Informe pelo menos um dia com horário (o iFood não aceita a loja sem nenhum)");
        }
        api.definirHorarios(merchantId, turnos);
        dormir();
        return horarios(merchantId);
    }

    // ------------------------------------------------------------------ apoio

    private void dormir() {
        if (esperaMs <= 0) {
            return;
        }
        try {
            Thread.sleep(esperaMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String hhmm(String t) {
        return t.length() >= 5 ? t.substring(0, 5) : t;
    }

    private static String texto(JsonNode n, String campo) {
        JsonNode v = n == null ? null : n.get(campo);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    /** Erro de regra vira 400; erro do iFood vira 502 (no controller). */
    public static boolean erroDoIfood(RuntimeException e) {
        return e instanceof IfoodException;
    }
}

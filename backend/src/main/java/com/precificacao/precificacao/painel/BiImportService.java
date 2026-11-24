package com.precificacao.precificacao.painel;

import com.precificacao.precificacao.reajuste.NomesParecidos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Importa o histórico do Power BI antigo (banco "pedidos", tabela gold.financeiro_periodo) — só leitura.
 * Depois liga cada operação do BI a uma loja do iFood: pelo id da loja guardado no BI, senão pelo nome.
 */
@Service
public class BiImportService {

    private static final Logger log = LoggerFactory.getLogger(BiImportService.class);
    public static final String BANCO_BI = "pedidos";
    static final double SEMELHANCA_MINIMA = 0.9;

    private static final String SQL = """
            SELECT f.id_financeiro, f.id_operacao, COALESCE(p.nome_plataforma, f.id_plataforma::text) AS plataforma,
                   o.nome_operacao AS operacao, l.nome_loja AS loja, upper(f.periodo_tipo) AS periodo_tipo,
                   f.data_inicio, f.data_fim, f.qtd_pedidos, f.valor_itens, f.ticket_medio, f.incentivos_ifood,
                   f.incentivos_loja, f.valor_bruto, f.valor_liquido, f.repasse, f.taxa_repasse, f.conversao,
                   f.novos_clientes, f.tempo_entrega, f.qtd_cancelamentos, f.tempo_aberto
            FROM gold.financeiro_periodo f
            LEFT JOIN silver.dim_operacao o ON o.id_operacao = f.id_operacao
            LEFT JOIN silver.dim_loja l ON l.id_loja = o.id_loja
            LEFT JOIN silver.dim_plataforma p ON p.id_plataforma = f.id_plataforma
            """;

    private static final String SQL_ALIAS = """
            SELECT a.id_operacao, a.id_loja_origem, a.nome_origem
            FROM silver.dim_operacao_alias a
            LEFT JOIN silver.dim_plataforma p ON p.id_plataforma = a.id_plataforma
            WHERE a.id_operacao IS NOT NULL AND (p.nome_plataforma ILIKE '%ifood%' OR a.id_plataforma = 1)
            """;

    public record Resultado(int linhas, int operacoes, int vinculadas, int semVinculo, String erro) {
    }

    private final PainelRepository repo;
    private final PainelProperties cfg;
    private final Clock clock;

    public BiImportService(PainelRepository repo, PainelProperties cfg, Clock clock) {
        this.repo = repo;
        this.cfg = cfg;
        this.clock = clock;
    }

    public synchronized Resultado importar() {
        if (!cfg.bancoBiConfigurado()) {
            return new Resultado(0, 0, 0, 0, "BI_DATABASE_URL não configurada");
        }
        OffsetDateTime agora = OffsetDateTime.now(clock);
        List<Map<String, Object>> linhas = new ArrayList<>();
        List<PainelRepository.Alias> aliases = new ArrayList<>();
        Jdbc j = jdbc(cfg.biDatabaseUrl(), BANCO_BI);
        Properties p = new Properties();
        p.setProperty("user", j.usuario());
        p.setProperty("password", j.senha());
        p.setProperty("readOnly", "true");
        p.setProperty("connectTimeout", "15");
        try (Connection c = DriverManager.getConnection(j.url(), p); Statement st = c.createStatement()) {
            c.setReadOnly(true);
            st.setFetchSize(1000);
            try (ResultSet rs = st.executeQuery(SQL)) {
                ResultSetMetaData md = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> r = new LinkedHashMap<>();
                    for (int i = 1; i <= md.getColumnCount(); i++) {
                        r.put(md.getColumnLabel(i), rs.getObject(i));
                    }
                    r.put("plataforma", plataforma((String) r.get("plataforma")));
                    linhas.add(r);
                }
            }
            try (ResultSet rs = st.executeQuery(SQL_ALIAS)) {
                while (rs.next()) {
                    aliases.add(new PainelRepository.Alias(rs.getInt(1), rs.getString(2), rs.getString(3)));
                }
            }
        } catch (SQLException e) {
            log.warn("[painel] falha ao ler o banco do BI: {}", e.getMessage());
            return new Resultado(0, 0, 0, 0, "Falha ao ler o banco do BI: " + e.getMessage());
        }
        int n;
        int[] v;
        try {
            n = repo.salvarBi(linhas, agora);
            repo.salvarAliases(aliases);
            v = vincular(agora);
        } catch (RuntimeException e) {
            Throwable causa = e;
            while (causa.getCause() != null) {
                causa = causa.getCause();
            }
            log.warn("[painel] falha ao gravar o histórico do BI", e);
            return new Resultado(0, 0, 0, 0, "Falha ao gravar o histórico do BI: " + causa.getMessage());
        }
        log.info("[painel] BI importado: {} linhas, {} operações vinculadas, {} sem vínculo", n, v[0], v[1]);
        return new Resultado(n, v[0] + v[1], v[0], v[1], null);
    }

    /** Refaz o vínculo das operações (por exemplo depois que a coleta descobriu os short ids). */
    public synchronized int[] revincular() {
        return vincular(OffsetDateTime.now(clock));
    }

    /**
     * Liga as operações iFood do BI às lojas, usando silver.dim_operacao_alias (nome e short id como estão
     * no iFood): 1) short id igual; 2) nome igual (normalizado); 3) nome muito parecido.
     * Vínculos feitos à mão não são trocados. Devolve {vinculadas, semVinculo}.
     */
    int[] vincular(OffsetDateTime agora) {
        Map<String, String> lojas = repo.nomesLojas();
        Map<String, String> porShort = repo.shortIds();
        Map<Integer, List<PainelRepository.Alias>> aliases = new LinkedHashMap<>();
        for (PainelRepository.Alias a : repo.aliases()) {
            aliases.computeIfAbsent(a.idOperacao(), k -> new ArrayList<>()).add(a);
        }
        int ok = 0, sem = 0;
        for (PainelRepository.OperacaoBi op : repo.operacoesBi()) {
            if (!"IFOOD".equals(op.plataforma())) {
                continue;
            }
            if (repo.vinculoManual(op.idOperacao())) {
                ok++;
                continue;
            }
            String[] r = escolher(aliases.getOrDefault(op.idOperacao(), List.of()), lojas, porShort);
            repo.vincular(op.idOperacao(), r[0], r[1], agora);
            if (r[0] != null) {
                ok++;
            } else {
                sem++;
            }
        }
        return new int[]{ok, sem};
    }

    /** {merchantId ou null, método}. */
    static String[] escolher(List<PainelRepository.Alias> aliases, Map<String, String> lojas, Map<String, String> porShort) {
        for (PainelRepository.Alias a : aliases) {
            String m = a.shortId() == null ? null : porShort.get(a.shortId().trim());
            if (m != null && lojas.containsKey(m)) {
                return new String[]{m, "ID"};
            }
        }
        for (PainelRepository.Alias a : aliases) {
            String alvo = NomesParecidos.normalizar(a.nomeOrigem());
            if (alvo.isBlank()) {
                continue;
            }
            List<String> iguais = lojas.entrySet().stream()
                    .filter(e -> alvo.equals(NomesParecidos.normalizar(e.getValue()))).map(Map.Entry::getKey).toList();
            if (iguais.size() == 1) {
                return new String[]{iguais.get(0), "NOME_IFOOD"};
            }
        }
        for (PainelRepository.Alias a : aliases) {
            String m = porNome(lojas, a.nomeOrigem(), null);
            if (m != null) {
                return new String[]{m, "NOME"};
            }
        }
        return new String[]{null, "NENHUM"};
    }

    /** Loja com o nome mais parecido; null se nenhuma passa do mínimo ou se há empate (nome ambíguo). */
    static String porNome(Map<String, String> lojas, String operacao, String loja) {
        String melhor = null;
        double nota = 0;
        boolean empate = false;
        for (Map.Entry<String, String> e : lojas.entrySet()) {
            if (e.getValue() == null) {
                continue;
            }
            double s = 0;
            for (String nome : new String[]{operacao, loja}) {
                if (nome != null && !nome.isBlank()) {
                    s = Math.max(s, NomesParecidos.semelhanca(nome, e.getValue()));
                }
            }
            if (s > nota + 1e-9) {
                nota = s;
                melhor = e.getKey();
                empate = false;
            } else if (Math.abs(s - nota) <= 1e-9 && s > 0) {
                empate = true;
            }
        }
        return nota >= SEMELHANCA_MINIMA && !empate ? melhor : null;
    }

    static String plataforma(String nome) {
        String n = nome == null ? "" : nome.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (n.contains("ifood") || n.equals("1")) {
            return "IFOOD";
        }
        if (n.contains("99") || n.equals("2")) {
            return "99FOOD";
        }
        return n.isBlank() ? "OUTRA" : n.toUpperCase();
    }

    public record Jdbc(String url, String usuario, String senha) {
    }

    /**
     * Converte "postgresql://usuario:senha@host:porta/banco?x=y" (formato do Railway) para JDBC,
     * trocando o banco pelo informado.
     */
    public static Jdbc jdbc(String url, String banco) {
        String u = url.trim();
        if (u.startsWith("jdbc:")) {
            u = u.substring(5);
        }
        URI uri = URI.create(u.replaceFirst("^postgres://", "postgresql://"));
        String usuario = "";
        String senha = "";
        if (uri.getRawUserInfo() != null) {
            String[] partes = uri.getRawUserInfo().split(":", 2);
            usuario = URLDecoder.decode(partes[0], StandardCharsets.UTF_8);
            senha = partes.length > 1 ? URLDecoder.decode(partes[1], StandardCharsets.UTF_8) : "";
        }
        int porta = uri.getPort() > 0 ? uri.getPort() : 5432;
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        return new Jdbc("jdbc:postgresql://" + uri.getHost() + ":" + porta + "/" + banco + query, usuario, senha);
    }
}

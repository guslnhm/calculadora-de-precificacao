import { useEffect, useRef, useState } from "react";
import { baseApi } from "../../services/api";
import { botao, caixa, campo, cor } from "./fmt";

// Consultas SQL de leitura na base histórica (banco "pedidos"). O backend só aceita um comando de leitura,
// numa transação só leitura, com 30 s de tempo máximo.

const ATALHOS = [
  { rotulo: "Último pedido", sql: "SELECT MAX(data_hora_pedido) FROM pedidos;" },
  {
    rotulo: "Pedidos por dia (40 dias)",
    sql: `SELECT data_hora_pedido::date AS dia, count(*) AS pedidos
FROM pedidos
WHERE data_hora_pedido >= now() - interval '40 days'
GROUP BY 1
ORDER BY 1 DESC;`,
  },
  {
    rotulo: "Gold da última semana",
    sql: `SELECT l.nome_loja, o.nome_operacao, g.qtd_pedidos, g.valor_itens, g.ticket_medio,
       g.valor_bruto, g.repasse, g.taxa_repasse, g.conversao, g.alteracao_cardapio
FROM gold.financeiro_periodo g
JOIN silver.dim_operacao o ON o.id_operacao = g.id_operacao
JOIN silver.dim_loja l ON l.id_loja = o.id_loja
WHERE g.periodo_tipo = 'SEMANAL'
  AND g.data_inicio = (SELECT max(data_inicio) FROM gold.financeiro_periodo WHERE periodo_tipo = 'SEMANAL')
ORDER BY g.valor_itens DESC;`,
  },
  {
    rotulo: "Tabelas e colunas",
    sql: `SELECT table_schema, table_name, column_name, data_type
FROM information_schema.columns
WHERE table_schema IN ('public', 'silver', 'gold')
ORDER BY 1, 2, ordinal_position;`,
  },
];

const CHAVE_HISTORICO = "consultaBase.historico";

function lerHistorico() {
  try {
    const h = JSON.parse(localStorage.getItem(CHAVE_HISTORICO) || "[]");
    return Array.isArray(h) ? h : [];
  } catch {
    return [];
  }
}

function guardarHistorico(h) {
  try {
    localStorage.setItem(CHAVE_HISTORICO, JSON.stringify(h));
  } catch {
    /* sem armazenamento: segue sem histórico */
  }
}

const numero = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 6 });

function mostrar(v) {
  if (v === null || v === undefined) return "";
  if (typeof v === "number") return numero.format(v);
  if (typeof v === "boolean") return v ? "true" : "false";
  return String(v);
}

function baixarCsv(r) {
  const celula = (v) => {
    if (v === null || v === undefined) return "";
    const t = typeof v === "number" ? String(v).replace(".", ",") : String(v);
    return /[;"\n\r]/.test(t) ? `"${t.replace(/"/g, '""')}"` : t;
  };
  const linhas = [r.colunas.map(celula).join(";"), ...r.linhas.map((l) => l.map(celula).join(";"))];
  const blob = new Blob(["﻿" + linhas.join("\r\n")], { type: "text/csv;charset=utf-8" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = `consulta-${new Date().toISOString().slice(0, 16).replace(/[:T]/g, "-")}.csv`;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}

export default function ConsultaBase() {
  const [sql, setSql] = useState(ATALHOS[0].sql);
  const [limite, setLimite] = useState(1000);
  const [rodando, setRodando] = useState(false);
  const [resultado, setResultado] = useState(null);
  const [erro, setErro] = useState("");
  const [historico, setHistorico] = useState(lerHistorico);
  const editor = useRef(null);

  useEffect(() => {
    editor.current?.focus();
  }, []);

  async function executar() {
    if (rodando || !sql.trim()) return;
    setRodando(true);
    setErro("");
    try {
      const r = await baseApi.consultar(sql, limite);
      setResultado(r);
      const h = [sql.trim(), ...historico.filter((x) => x !== sql.trim())].slice(0, 15);
      setHistorico(h);
      guardarHistorico(h);
    } catch (e) {
      setErro(e.message);
      setResultado(null);
    } finally {
      setRodando(false);
    }
  }

  function teclas(e) {
    if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
      e.preventDefault();
      executar();
    }
  }

  return (
    <div style={{ display: "grid", gap: 12 }}>
      <div style={{ ...caixa, display: "grid", gap: 10 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap", alignItems: "center" }}>
          <span style={{ fontSize: 12, color: cor.textoSuave, marginRight: 4 }}>Atalhos:</span>
          {ATALHOS.map((a) => (
            <button key={a.rotulo} onClick={() => setSql(a.sql)}
                    style={{ ...campo, padding: "4px 10px", fontSize: 12, cursor: "pointer" }}>{a.rotulo}</button>
          ))}
          {historico.length > 0 && (
            <select value="" onChange={(e) => e.target.value && setSql(e.target.value)}
                    style={{ ...campo, padding: "4px 8px", fontSize: 12, marginLeft: "auto", maxWidth: 260 }}>
              <option value="">Últimas consultas…</option>
              {historico.map((h, i) => <option key={i} value={h}>{h.replace(/\s+/g, " ").slice(0, 80)}</option>)}
            </select>
          )}
        </div>
        <textarea ref={editor} value={sql} onChange={(e) => setSql(e.target.value)} onKeyDown={teclas}
                  spellCheck={false} rows={8}
                  style={{ ...campo, fontFamily: "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace",
                           fontSize: 13, lineHeight: 1.5, resize: "vertical", width: "100%", boxSizing: "border-box" }} />
        <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
          <button style={botao} onClick={executar} disabled={rodando}>{rodando ? "Executando…" : "Executar"}</button>
          <span style={{ fontSize: 12, color: cor.textoSuave }}>Ctrl+Enter</span>
          <label style={{ fontSize: 12, color: cor.textoSuave, display: "flex", gap: 6, alignItems: "center" }}>
            Até
            <select value={limite} onChange={(e) => setLimite(Number(e.target.value))} style={{ ...campo, padding: "4px 8px" }}>
              {[100, 1000, 5000, 10000].map((n) => <option key={n} value={n}>{n.toLocaleString("pt-BR")}</option>)}
            </select>
            linhas
          </label>
          <span style={{ fontSize: 12, color: cor.textoSuave, marginLeft: "auto" }}>
            Só leitura: SELECT, WITH, EXPLAIN, SHOW. Nada é alterado na base.
          </span>
        </div>
        {erro && <div style={{ fontSize: 13, color: cor.ruim, whiteSpace: "pre-wrap" }}>{erro}</div>}
      </div>

      {resultado && (
        <div style={{ ...caixa, padding: 0, overflow: "hidden" }}>
          <div style={{ display: "flex", gap: 10, alignItems: "center", padding: "10px 14px", borderBottom: `1px solid ${cor.grade}`, fontSize: 13 }}>
            <span>
              {resultado.linhas.length.toLocaleString("pt-BR")} linha(s)
              {resultado.cortado && <span style={{ color: cor.ruim }}> (cortado no limite; aumente o limite ou filtre)</span>}
              <span style={{ color: cor.textoSuave }}> · {resultado.ms} ms</span>
            </span>
            {resultado.colunas.length > 0 && (
              <button style={{ ...botao, padding: "4px 10px", fontSize: 12, marginLeft: "auto" }} onClick={() => baixarCsv(resultado)}>
                Baixar CSV
              </button>
            )}
          </div>
          {resultado.colunas.length > 0 && (
            <div style={{ overflow: "auto", maxHeight: "60vh" }}>
              <table style={{ borderCollapse: "collapse", fontSize: 12.5, width: "max-content", minWidth: "100%" }}>
                <thead>
                  <tr>
                    {resultado.colunas.map((c, i) => (
                      <th key={i} style={{ position: "sticky", top: 0, background: cor.campo, textAlign: "left",
                                           padding: "7px 12px", borderBottom: `1px solid ${cor.bordaCampo}`, whiteSpace: "nowrap" }}>{c}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {resultado.linhas.map((l, i) => (
                    <tr key={i}>
                      {l.map((v, j) => (
                        <td key={j} style={{ padding: "5px 12px", borderBottom: `1px solid ${cor.grade}`, whiteSpace: "nowrap",
                                             textAlign: typeof v === "number" ? "right" : "left",
                                             color: v === null ? cor.textoSuave : cor.texto, fontVariantNumeric: "tabular-nums" }}>
                          {v === null ? "null" : mostrar(v)}
                        </td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

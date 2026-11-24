import { useState } from "react";
import { baseApi } from "../../services/api";
import { botao, caixa, campo, cor } from "./fmt";

// Importação em lote dos valores financeiros do Portal (planilha CSV) e reprocessamento de pedidos.

export function ImportarValoresPortal({ onImportado }) {
  const [arquivo, setArquivo] = useState(null);
  const [msg, setMsg] = useState("");
  const [enviando, setEnviando] = useState(false);

  async function enviar() {
    if (!arquivo) return;
    setEnviando(true);
    setMsg("Importando…");
    try {
      const r = await baseApi.importarValoresPortal(arquivo);
      setMsg(`${r.linhas} linha(s) gravadas na base; a gold foi recalculada e o painel está sendo atualizado.`);
      onImportado?.();
    } catch (e) {
      setMsg(e.message);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div style={{ ...caixa, display: "grid", gap: 8, marginBottom: 12 }}>
      <div style={{ fontWeight: 700 }}>Importar planilha de valores do Portal</div>
      <div style={{ fontSize: 12, color: cor.textoSuave, lineHeight: 1.5 }}>
        CSV com ";" e as colunas id_operacao, inicio (segunda-feira da semana), valor_bruto, valor_liquido,
        recebido_via_loja e repassado_ifood. Grava só esses valores (conversão, novos clientes, tempos e alteração de
        cardápio ficam como estão) e recalcula a gold de todas as semanas de uma vez.
      </div>
      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
        <input type="file" accept=".csv,text/csv" onChange={(e) => setArquivo(e.target.files?.[0] || null)} style={{ fontSize: 13 }} />
        <button style={botao} onClick={enviar} disabled={!arquivo || enviando}>{enviando ? "Importando…" : "Importar"}</button>
        {msg && <span style={{ fontSize: 13, color: cor.textoSuave }}>{msg}</span>}
      </div>
    </div>
  );
}

export function ReprocessarPedidos() {
  const [de, setDe] = useState("");
  const [ate, setAte] = useState("");
  const [msg, setMsg] = useState("");
  const [rodando, setRodando] = useState(false);

  async function rodar() {
    setRodando(true);
    setMsg("Reprocessando…");
    try {
      const r = await baseApi.reprocessar(de, ate);
      setMsg(`${r.pedidos.toLocaleString("pt-BR")} pedido(s) lidos, ${r.noFato.toLocaleString("pt-BR")} na silver; `
        + `${r.semanas} semana(s) e ${r.meses} mês(es) recalculados na gold.`);
    } catch (e) {
      setMsg(e.message);
    } finally {
      setRodando(false);
    }
  }

  return (
    <div style={{ display: "grid", gap: 6, fontSize: 13 }}>
      <div style={{ fontWeight: 600 }}>Reprocessar pedidos que já estão na base</div>
      <div style={{ fontSize: 12, color: cor.textoSuave }}>
        Passa de novo para a silver e a gold os pedidos da tabela pedidos no período (até 120 dias).
      </div>
      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
        <label>De <input type="date" value={de} onChange={(e) => setDe(e.target.value)} style={campo} /></label>
        <label>até <input type="date" value={ate} onChange={(e) => setAte(e.target.value)} style={campo} /></label>
        <button style={botao} onClick={rodar} disabled={!de || !ate || rodando}>{rodando ? "Reprocessando…" : "Reprocessar"}</button>
        {msg && <span style={{ color: cor.textoSuave }}>{msg}</span>}
      </div>
    </div>
  );
}

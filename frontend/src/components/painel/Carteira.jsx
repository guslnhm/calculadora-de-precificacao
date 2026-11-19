import { useMemo, useState } from "react";
import { caixa, campo, cor, FONTES, inteiro, minutos, percentual, reais, reaisCurto, variacao, vazio } from "./fmt";

/** Cartão de número grande com variação. */
export function Cartao({ titulo, valor, delta, nota, destaque }) {
  return (
    <div style={{ ...caixa, padding: "14px 16px", minWidth: 0, borderColor: destaque || cor.borda }}>
      <div style={{ fontSize: 12, color: cor.textoSuave, textTransform: "uppercase", letterSpacing: 0.4 }}>{titulo}</div>
      <div style={{ fontSize: "clamp(17px, 1.2vw + 12px, 22px)", fontWeight: 600, letterSpacing: "-0.02em", color: cor.texto, margin: "6px 0 4px", lineHeight: 1.2, overflowWrap: "anywhere" }}>
        {valor}
      </div>
      <div style={{ fontSize: 12, display: "flex", gap: 8, flexWrap: "wrap" }}>
        {delta && <span style={{ color: delta.cor, fontWeight: 600 }}>{delta.texto}</span>}
        {nota && <span style={{ color: cor.textoSuave }}>{nota}</span>}
      </div>
    </div>
  );
}

export function Selo({ fonte }) {
  const f = FONTES[fonte];
  if (!f) return null;
  return (
    <span title={f.dica} style={{ fontSize: 10, padding: "2px 6px", borderRadius: 999, border: `1px solid ${cor.borda}`, color: cor.textoSuave, whiteSpace: "nowrap" }}>
      {f.rotulo}
    </span>
  );
}

const COLUNAS = [
  { id: "nome", rotulo: "Loja", valor: (l) => l.nome?.toLowerCase() },
  { id: "faturamento", rotulo: "Faturamento", valor: (l) => l.atual?.faturamento, num: true },
  { id: "vFat", rotulo: "Δ fat.", valor: (l) => l.variacao?.faturamento, num: true },
  { id: "pedidos", rotulo: "Pedidos", valor: (l) => l.atual?.pedidos, num: true },
  { id: "vPed", rotulo: "Δ ped.", valor: (l) => l.variacao?.pedidos, num: true },
  { id: "ticket", rotulo: "Ticket", valor: (l) => l.atual?.ticket, num: true },
  { id: "cancel", rotulo: "Cancel.", valor: (l) => l.atual?.taxaCancelamento, num: true },
  { id: "conversao", rotulo: "Conversão", valor: (l) => l.atual?.conversao, num: true },
  { id: "repasse", rotulo: "Repasse", valor: (l) => l.atual?.repasse, num: true },
  { id: "fechada", rotulo: "Fechada", valor: (l) => l.atual?.minutosFechada, num: true },
  { id: "perda", rotulo: "Perda est.", valor: (l) => l.atual?.perdaEstimada, num: true },
];

function Destaques({ titulo, linhas, valor, cor: c, onAbrir }) {
  return (
    <div style={{ ...caixa, minWidth: 0 }}>
      <div style={{ fontWeight: 700, marginBottom: 10 }}>{titulo}</div>
      {linhas.length === 0 && <div style={{ color: cor.textoSuave, fontSize: 13 }}>Nada a destacar.</div>}
      {linhas.map((l) => (
        <div key={l.chave} onClick={() => onAbrir(l)} style={{ cursor: "pointer", display: "flex", justifyContent: "space-between", gap: 10, fontSize: 13, padding: "5px 0", borderTop: `1px solid ${cor.grade}` }}>
          <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{l.nome}</span>
          <span style={{ color: c, fontWeight: 600, whiteSpace: "nowrap" }}>{valor(l)}</span>
        </div>
      ))}
    </div>
  );
}

export default function Carteira({ dados, onAbrirLoja }) {
  const [busca, setBusca] = useState("");
  const [plataforma, setPlataforma] = useState("");
  const [ordem, setOrdem] = useState({ id: "faturamento", desc: true });

  const linhas = useMemo(() => {
    const t = busca.trim().toLowerCase();
    const col = COLUNAS.find((c) => c.id === ordem.id);
    return (dados?.linhas || [])
      .filter((l) => !t || (l.nome || "").toLowerCase().includes(t))
      .filter((l) => !plataforma || l.plataforma === plataforma)
      .sort((a, b) => {
        const va = col.valor(a);
        const vb = col.valor(b);
        if (vazio(va) && vazio(vb)) return 0;
        if (vazio(va)) return 1;
        if (vazio(vb)) return -1;
        const r = col.num ? Number(va) - Number(vb) : String(va).localeCompare(String(vb));
        return ordem.desc ? -r : r;
      });
  }, [dados, busca, plataforma, ordem]);

  if (!dados) return null;
  const t = dados.totais || {};
  const comDados = (dados.linhas || []).filter((l) => l.atual?.faturamento != null && l.anterior?.faturamento != null);
  const quedas = [...comDados].filter((l) => Number(l.variacao?.faturamento) < 0)
    .sort((a, b) => a.variacao.faturamento - b.variacao.faturamento).slice(0, 5);
  const altas = [...comDados].filter((l) => Number(l.variacao?.faturamento) > 0)
    .sort((a, b) => b.variacao.faturamento - a.variacao.faturamento).slice(0, 5);
  const fechadas = (dados.linhas || []).filter((l) => Number(l.atual?.perdaEstimada) > 0)
    .sort((a, b) => b.atual.perdaEstimada - a.atual.perdaEstimada).slice(0, 5);
  const plataformas = [...new Set((dados.linhas || []).map((l) => l.plataforma))];

  return (
    <div style={{ display: "grid", gap: 16, minWidth: 0, gridTemplateColumns: "minmax(0, 1fr)" }}>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(140px, 100%), 1fr))", gap: 12 }}>
        <Cartao titulo="Faturamento" valor={reaisCurto(t.faturamento)} delta={variacao(t.variacaoFaturamento)} nota="vs período anterior" />
        <Cartao titulo="Pedidos" valor={inteiro(t.pedidos)} delta={variacao(t.variacaoPedidos)} nota="vs período anterior" />
        <Cartao titulo="Ticket médio" valor={reais(t.ticket)} />
        <Cartao titulo="Cancelamentos" valor={inteiro(t.cancelamentos)} />
        <Cartao titulo="Perda estimada" valor={reaisCurto(t.perdaEstimada)} nota={`${minutos(t.minutosFechada)} fechadas`} destaque={Number(t.perdaEstimada) > 0 ? "#F2B79B" : undefined} />
        <Cartao titulo="Lojas com dados" valor={inteiro(t.lojas)} />
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(260px, 100%), 1fr))", gap: 12 }}>
        <Destaques titulo="▼ Maiores quedas" onAbrir={onAbrirLoja} linhas={quedas} cor={cor.ruim} valor={(l) => variacao(l.variacao.faturamento).texto} />
        <Destaques titulo="▲ Maiores altas" onAbrir={onAbrirLoja} linhas={altas} cor={cor.bom} valor={(l) => variacao(l.variacao.faturamento).texto} />
        <Destaques titulo="Mais vendas perdidas (fechada)" onAbrir={onAbrirLoja} linhas={fechadas} cor={cor.texto} valor={(l) => `${reais(l.atual.perdaEstimada, true)} · ${minutos(l.atual.minutosFechada)}`} />
      </div>

      <div style={caixa}>
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 12, alignItems: "center" }}>
          <input placeholder="Buscar loja..." value={busca} onChange={(e) => setBusca(e.target.value)} style={campo} />
          {plataformas.length > 1 && (
            <select value={plataforma} onChange={(e) => setPlataforma(e.target.value)} style={campo}>
              <option value="">Todas as plataformas</option>
              {plataformas.map((p) => <option key={p} value={p}>{p === "IFOOD" ? "iFood" : p === "99FOOD" ? "99Food" : p}</option>)}
            </select>
          )}
          <span style={{ color: cor.textoSuave, fontSize: 12, marginLeft: "auto" }}>{linhas.length} loja(s) · clique numa loja para ver o boletim</span>
        </div>
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
            <thead>
              <tr>
                {COLUNAS.map((c) => (
                  <th
                    key={c.id}
                    onClick={() => setOrdem((o) => ({ id: c.id, desc: o.id === c.id ? !o.desc : c.num }))}
                    style={{ border: "none", textAlign: c.num ? "right" : "left", padding: "8px 10px", color: cor.textoSuave, fontWeight: 600, cursor: "pointer", whiteSpace: "nowrap", borderBottom: `1px solid ${cor.borda}` }}
                  >
                    {c.rotulo} {ordem.id === c.id ? (ordem.desc ? "↓" : "↑") : ""}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {linhas.map((l) => {
                const a = l.atual || {};
                const vf = variacao(l.variacao?.faturamento);
                const vp = variacao(l.variacao?.pedidos);
                return (
                  <tr key={l.chave} onClick={() => onAbrirLoja(l)} style={{ cursor: "pointer", borderBottom: `1px solid ${cor.grade}` }}
                      onMouseEnter={(e) => (e.currentTarget.style.background = cor.campo)}
                      onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}>
                    <td style={{ border: "none", padding: "8px 10px", maxWidth: 280 }}>
                      <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
                        <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{l.nome}</span>
                        {l.plataforma !== "IFOOD" && <span style={{ fontSize: 10, color: cor.amarelo }}>{l.plataforma === "99FOOD" ? "99Food" : l.plataforma}</span>}
                        <Selo fonte={a.fonte} />
                      </div>
                    </td>
                    <td style={td}>{reais(a.faturamento)}</td>
                    <td style={{ ...td, color: vf.cor }}>{vf.texto}</td>
                    <td style={td}>{inteiro(a.pedidos)}</td>
                    <td style={{ ...td, color: vp.cor }}>{vp.texto}</td>
                    <td style={td}>{reais(a.ticket)}</td>
                    <td style={td}>{percentual(a.taxaCancelamento)}</td>
                    <td style={td}>{percentual(a.conversao)}</td>
                    <td style={td}>{reais(a.repasse, true)}</td>
                    <td style={td}>{a.minutosFechada ? minutos(a.minutosFechada) : "—"}</td>
                    <td style={{ ...td, color: Number(a.perdaEstimada) > 0 ? cor.ruim : cor.textoSuave }}>{Number(a.perdaEstimada) > 0 ? reais(a.perdaEstimada, true) : "—"}</td>
                  </tr>
                );
              })}
              {linhas.length === 0 && (
                <tr><td colSpan={COLUNAS.length} style={{ border: "none", padding: 20, textAlign: "center", color: cor.textoSuave }}>Sem dados neste período.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

const td = { border: "none", padding: "8px 10px", textAlign: "right", whiteSpace: "nowrap" };

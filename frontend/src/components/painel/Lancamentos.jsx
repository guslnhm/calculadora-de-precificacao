import { useState } from "react";
import { painelApi } from "../../services/api";
import { botao, caixa, campo, cor, percentual, reais, rotuloPeriodo } from "./fmt";

const num = (s) => (s.trim() === "" ? null : Number(s.replace(/\./g, "").replace(",", ".")));
const texto = (v, mult = 1) => (v == null ? "" : String(+(v * mult).toFixed(2)).replace(".", ","));

function Linha({ l, periodo, onSalvo }) {
  const a = l.atual || {};
  const [conv, setConv] = useState(texto(a.conversao, 100));
  const [rep, setRep] = useState(texto(a.repasse));
  const [estado, setEstado] = useState("");
  const mudou = conv !== texto(a.conversao, 100) || rep !== texto(a.repasse);

  async function salvar() {
    setEstado("...");
    try {
      await painelApi.salvarManual({ chave: l.chave, periodoTipo: periodo.tipo, inicio: periodo.inicio,
        conversao: num(conv) == null ? null : num(conv) / 100, repasse: num(rep) });
      setEstado("✓");
      onSalvo();
    } catch (e) {
      setEstado(e.message);
    }
  }

  return (
    <tr style={{ borderBottom: `1px solid ${cor.grade}` }}>
      <td style={{ padding: "6px 10px" }}>{l.nome}</td>
      <td style={{ padding: "6px 10px", textAlign: "right", color: cor.textoSuave }}>{reais(a.faturamento, true)}</td>
      <td style={{ padding: "6px 10px" }}>
        <input value={conv} onChange={(e) => setConv(e.target.value)} placeholder="%" style={{ ...campo, width: 70 }} />
        {a.conversaoManual === false && a.conversao != null && <span style={{ fontSize: 10, color: cor.textoSuave, marginLeft: 6 }}>do BI</span>}
      </td>
      <td style={{ padding: "6px 10px" }}>
        <input value={rep} onChange={(e) => setRep(e.target.value)} placeholder="R$" style={{ ...campo, width: 110 }} />
      </td>
      <td style={{ padding: "6px 10px", whiteSpace: "nowrap" }}>
        <button onClick={salvar} disabled={!mudou} style={{ ...botao, padding: "6px 10px", opacity: mudou ? 1 : 0.4 }}>Salvar</button>
        <span style={{ marginLeft: 8, fontSize: 12, color: estado === "✓" ? cor.bom : cor.textoSuave }}>{estado}</span>
      </td>
    </tr>
  );
}

/** Lançamento em lote de conversão e repasse do período (valores do Portal do Parceiro). */
export default function Lancamentos({ dados, onSalvo }) {
  const [busca, setBusca] = useState("");
  const [soPendentes, setSoPendentes] = useState(false);
  if (!dados) return null;
  const p = dados.periodo;
  const linhas = (dados.linhas || [])
    .filter((l) => !busca || (l.nome || "").toLowerCase().includes(busca.toLowerCase()))
    .filter((l) => !soPendentes || l.atual?.conversao == null || l.atual?.repasse == null)
    .sort((a, b) => (a.nome || "").localeCompare(b.nome || ""));
  const pendentes = (dados.linhas || []).filter((l) => l.atual?.conversao == null || l.atual?.repasse == null).length;

  return (
    <div style={caixa}>
      <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", marginBottom: 12 }}>
        <div>
          <div style={{ fontWeight: 700 }}>Conversão e repasse — {rotuloPeriodo(p)}</div>
          <div style={{ fontSize: 12, color: cor.textoSuave }}>
            Copie do Portal do Parceiro. Conversão em % (ex.: 19,2). {pendentes} loja(s) sem algum dos dois.
          </div>
        </div>
        <input placeholder="Buscar loja..." value={busca} onChange={(e) => setBusca(e.target.value)} style={{ ...campo, marginLeft: "auto" }} />
        <label style={{ fontSize: 13, display: "flex", gap: 6, alignItems: "center" }}>
          <input type="checkbox" checked={soPendentes} onChange={(e) => setSoPendentes(e.target.checked)} /> só pendentes
        </label>
      </div>
      <div style={{ overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
          <thead>
            <tr style={{ color: cor.textoSuave, textAlign: "left" }}>
              <th style={{ padding: "6px 10px" }}>Loja</th>
              <th style={{ padding: "6px 10px", textAlign: "right" }}>Faturamento</th>
              <th style={{ padding: "6px 10px" }}>Conversão (%)</th>
              <th style={{ padding: "6px 10px" }}>Repasse (R$)</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {linhas.map((l) => (
              <Linha key={`${l.chave}-${p.inicio}-${percentual(l.atual?.conversao)}-${l.atual?.repasse}`} l={l} periodo={p} onSalvo={onSalvo} />
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

import { useCallback, useEffect, useState } from "react";
import { baseApi } from "../../services/api";
import { ImportarValoresPortal } from "./ImportarValoresPortal";
import { botao, caixa, campo, cor, inteiro, percentual, reais, rotuloPeriodo } from "./fmt";

// Lançamento dos valores do Portal do Parceiro direto na base histórica (silver.fato_metricas_periodo),
// no lugar de editar o banco à mão. Ao salvar, a gold do período é refeita e o painel reimporta.

const num = (s) => {
  const t = String(s ?? "").trim();
  if (t === "") return null;
  const n = Number(t.replace(/\s/g, "").replace(/\.(?=\d{3}(\D|$))/g, "").replace(",", "."));
  return Number.isFinite(n) ? n : NaN;
};
const txt = (v, mult = 1) => (v == null ? "" : String(+(Number(v) * mult).toFixed(2)).replace(".", ","));

const CAMPOS = [
  { id: "valorBruto", rotulo: "Valor bruto (R$)", largura: 110 },
  { id: "valorLiquido", rotulo: "Valor líquido (R$)", largura: 110 },
  { id: "recebidoViaLoja", rotulo: "Recebido via loja (R$)", largura: 100 },
  { id: "repassadoPlataforma", rotulo: "Repassado ao iFood (R$)", largura: 100 },
  { id: "conversao", rotulo: "Conversão (%)", largura: 70, pct: true },
  { id: "novosClientes", rotulo: "Novos clientes", largura: 70, inteiro: true },
  { id: "tempoEntrega", rotulo: "Tempo entrega (min)", largura: 70 },
  { id: "tempoAberto", rotulo: "Tempo aberto (%)", largura: 70, pct: true },
];

function inicial(m) {
  const v = {};
  CAMPOS.forEach((c) => {
    v[c.id] = txt(m[c.id], c.pct ? 100 : 1);
  });
  v.alteracaoCardapio = !!m.alteracaoCardapio;
  return v;
}

function Linha({ m, periodo, onSalvo }) {
  const [v, setV] = useState(() => inicial(m));
  const [estado, setEstado] = useState("");
  const base = inicial(m);
  const mudou = CAMPOS.some((c) => v[c.id] !== base[c.id]) || v.alteracaoCardapio !== base.alteracaoCardapio;
  const invalido = CAMPOS.some((c) => Number.isNaN(num(v[c.id])));

  async function salvar() {
    setEstado("salvando…");
    const dados = { idOperacao: m.idOperacao, periodoTipo: periodo.tipo, inicio: periodo.inicio, alteracaoCardapio: v.alteracaoCardapio };
    CAMPOS.forEach((c) => {
      const n = num(v[c.id]);
      dados[c.id] = n == null ? null : c.pct ? n / 100 : c.inteiro ? Math.round(n) : n;
    });
    try {
      await baseApi.salvarMetrica(dados);
      setEstado("✓");
      onSalvo();
    } catch (e) {
      setEstado(e.message);
    }
  }

  return (
    <tr style={{ borderBottom: `1px solid ${cor.grade}` }}>
      <td style={{ padding: "6px 8px", minWidth: 180 }}>
        <div style={{ fontWeight: 600 }}>{m.loja}</div>
        <div style={{ fontSize: 11, color: cor.textoSuave }}>{m.operacao}</div>
      </td>
      <td style={{ padding: "6px 8px", textAlign: "right", color: cor.textoSuave, whiteSpace: "nowrap" }}>
        {m.pedidos == null ? "—" : inteiro(m.pedidos)}
        <div style={{ fontSize: 11 }}>{m.faturamento == null ? "" : reais(m.faturamento, true)}</div>
      </td>
      {CAMPOS.map((c) => (
        <td key={c.id} style={{ padding: "6px 4px" }}>
          <input value={v[c.id]} onChange={(e) => setV({ ...v, [c.id]: e.target.value })} aria-label={`${c.rotulo} · ${m.loja} ${m.operacao}`}
                 style={{ ...campo, width: c.largura, padding: "6px 8px", borderColor: Number.isNaN(num(v[c.id])) ? cor.ruim : undefined }} />
        </td>
      ))}
      <td style={{ padding: "6px 8px", textAlign: "center" }}>
        <input type="checkbox" checked={v.alteracaoCardapio} onChange={(e) => setV({ ...v, alteracaoCardapio: e.target.checked })}
               aria-label={`Alteração de cardápio · ${m.loja} ${m.operacao}`} />
      </td>
      <td style={{ padding: "6px 8px", whiteSpace: "nowrap", fontSize: 12, color: cor.textoSuave }}>
        {m.repasse == null ? "—" : reais(m.repasse, true)}
        <div>{m.taxaRepasse == null ? "" : `taxa ${percentual(m.taxaRepasse)}`}</div>
      </td>
      <td style={{ padding: "6px 8px", whiteSpace: "nowrap" }}>
        <button onClick={salvar} disabled={!mudou || invalido} style={{ ...botao, padding: "6px 10px", opacity: mudou && !invalido ? 1 : 0.4 }}>Salvar</button>
        <div style={{ fontSize: 11, marginTop: 2, color: estado === "✓" ? cor.bom : cor.textoSuave, maxWidth: 160 }}>{estado}</div>
      </td>
    </tr>
  );
}

export default function LancamentosBase({ periodo, onSalvo }) {
  const [linhas, setLinhas] = useState(null);
  const [erro, setErro] = useState("");
  const [busca, setBusca] = useState("");
  const [soPendentes, setSoPendentes] = useState(false);

  const carregar = useCallback(() => {
    if (!periodo) return;
    baseApi.metricas(periodo.tipo, periodo.inicio).then((l) => {
      setLinhas(l);
      setErro("");
    }).catch((e) => setErro(e.message));
  }, [periodo]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  if (!periodo) return null;
  const pendente = (m) => m.valorBruto == null || m.valorLiquido == null || m.conversao == null;
  const lista = (linhas || [])
    .filter((m) => !busca || `${m.loja} ${m.operacao}`.toLowerCase().includes(busca.toLowerCase()))
    .filter((m) => !soPendentes || pendente(m));
  const pendentes = (linhas || []).filter(pendente).length;

  return (
    <>
    <ImportarValoresPortal onImportado={() => { carregar(); onSalvo?.(); }} />
    <div style={caixa}>
      <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", marginBottom: 12 }}>
        <div style={{ minWidth: 0 }}>
          <div style={{ fontWeight: 700 }}>Valores do Portal — {rotuloPeriodo(periodo)}</div>
          <div style={{ fontSize: 12, color: cor.textoSuave, lineHeight: 1.5 }}>
            Grava na base histórica (silver) e refaz a gold do período. Percentuais em % (ex.: 19,2).
            Repasse = líquido + recebido via loja; taxa = repasse ÷ (bruto − repassado ao iFood).
            {linhas && ` ${pendentes} operação(ões) sem bruto, líquido ou conversão.`}
          </div>
        </div>
        <input placeholder="Buscar loja..." value={busca} onChange={(e) => setBusca(e.target.value)} style={{ ...campo, marginLeft: "auto" }} />
        <label style={{ fontSize: 13, display: "flex", gap: 6, alignItems: "center" }}>
          <input type="checkbox" checked={soPendentes} onChange={(e) => setSoPendentes(e.target.checked)} /> só pendentes
        </label>
      </div>
      {erro && <div style={{ color: cor.ruim, fontSize: 13, marginBottom: 8 }}>{erro}</div>}
      {!linhas && !erro && <div style={{ color: cor.textoSuave, fontSize: 13 }}>Carregando a base…</div>}
      {linhas && (
        <div style={{ overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13, minWidth: 1250 }}>
            <thead>
              <tr style={{ color: cor.textoSuave, textAlign: "left", fontSize: 11 }}>
                <th style={{ padding: "6px 8px" }}>Loja · operação</th>
                <th style={{ padding: "6px 8px", textAlign: "right" }}>Pedidos</th>
                {CAMPOS.map((c) => <th key={c.id} style={{ padding: "6px 4px", fontWeight: 600 }}>{c.rotulo}</th>)}
                <th style={{ padding: "6px 8px" }}>Cardápio alterado</th>
                <th style={{ padding: "6px 8px" }}>Repasse</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {lista.map((m) => (
                <Linha key={`${m.idOperacao}-${periodo.tipo}-${periodo.inicio}`} m={m} periodo={periodo}
                       onSalvo={() => { carregar(); onSalvo?.(); }} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
    </>
  );
}

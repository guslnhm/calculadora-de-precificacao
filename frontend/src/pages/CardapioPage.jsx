import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { cardapioApi } from "../services/api";
import { cores, desdeAgora, pilula, s } from "../components/monitor/ui";
import CardapioLoja from "../components/cardapio/CardapioLoja";

const PROBLEMAS = {
  PAUSADO: { rotulo: "pausado", cor: cores.amarelo },
  CATEGORIA: { rotulo: "categoria pausada", cor: cores.amarelo },
  NAO_VENDAVEL: { rotulo: "não vendável", cor: cores.vermelho },
};

/** O que está parado em todas as lojas, agrupado por loja. */
function ParadosAgora({ onAbrir }) {
  const [linhas, setLinhas] = useState(null);
  const [erro, setErro] = useState("");

  useEffect(() => {
    cardapioApi
      .pausados()
      .then(setLinhas)
      .catch((e) => setErro(e.message));
  }, []);

  const porLoja = useMemo(() => {
    const m = new Map();
    (linhas || []).forEach((l) => {
      if (!m.has(l.merchant_id)) m.set(l.merchant_id, { merchant_id: l.merchant_id, loja: l.loja, itens: [] });
      m.get(l.merchant_id).itens.push(l);
    });
    return [...m.values()].sort((a, b) => b.itens.length - a.itens.length);
  }, [linhas]);

  return (
    <div style={s.card}>
      <h3 style={s.cardTitulo}>Parados agora em todas as lojas</h3>
      {erro && <div style={s.erro}>{erro}</div>}
      {linhas === null && !erro && <div style={s.suave}>Carregando…</div>}
      {linhas !== null && porLoja.length === 0 && (
        <div style={s.vazio}>
          Nenhum item parado. ✅
          <div style={{ ...s.suave, marginTop: "6px" }}>
            O cardápio de cada loja é relido a cada 10 minutos. Escolha uma loja acima para ver tudo.
          </div>
        </div>
      )}
      <div style={{ display: "grid", gap: "12px" }}>
        {porLoja.map((l) => (
          <div key={l.merchant_id} style={{ borderBottom: `1px solid ${cores.borda}`, paddingBottom: "10px" }}>
            <div style={{ display: "flex", gap: "8px", alignItems: "center", marginBottom: "6px", flexWrap: "wrap" }}>
              <button
                onClick={() => onAbrir(l.merchant_id)}
                style={{
                  background: "transparent",
                  border: "none",
                  color: cores.texto,
                  fontWeight: "bold",
                  fontSize: "15px",
                  cursor: "pointer",
                  padding: 0,
                }}
              >
                {l.loja} →
              </button>
              <span style={s.suave}>{l.itens.length} parado(s)</span>
            </div>
            <div style={{ display: "flex", flexWrap: "wrap", gap: "6px" }}>
              {l.itens.slice(0, 12).map((i) => {
                const p = PROBLEMAS[i.problema] || { rotulo: i.problema, cor: cores.cinza };
                return (
                  <span
                    key={i.id}
                    style={pilula(p.cor)}
                    title={`${p.rotulo}${i.detalhe ? ` (${i.detalhe})` : ""} · ${i.categoria || ""} · há ${desdeAgora(i.inicio)}`}
                  >
                    {i.problema === "CATEGORIA" ? `categoria ${i.categoria}` : i.nome}
                    {i.tipo === "COMPLEMENTO" ? " (compl.)" : ""} · {desdeAgora(i.inicio)}
                  </span>
                );
              })}
              {l.itens.length > 12 && <span style={s.suave}>e mais {l.itens.length - 12}</span>}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default function CardapioPage() {
  const [params, setParams] = useSearchParams();
  const merchantId = params.get("loja") || "";
  const [lojas, setLojas] = useState(null);
  const [erro, setErro] = useState("");

  const carregarLojas = useCallback(() => {
    cardapioApi
      .lojas()
      .then((d) => (setLojas(d), setErro("")))
      .catch((e) => setErro(e.message));
  }, []);

  useEffect(() => {
    carregarLojas();
  }, [carregarLojas]);

  const loja = (lojas || []).find((l) => l.merchant_id === merchantId);

  function escolher(id) {
    setParams(id ? { loja: id } : {});
  }

  return (
    <div style={s.page}>
      <div style={s.header}>
        <div>
          <h2 style={s.titulo}>Cardápio iFood</h2>
          <p style={s.subtitulo}>Itens de cada loja, o que está pausado e mudança de preço e status direto no iFood.</p>
        </div>
      </div>

      {erro && <div style={s.erro}>Não consegui carregar as lojas: {erro}</div>}

      <div style={{ display: "flex", gap: "10px", alignItems: "center", flexWrap: "wrap", marginBottom: "16px" }}>
        <select
          style={{ ...s.input, maxWidth: "420px" }}
          value={merchantId}
          onChange={(e) => escolher(e.target.value)}
          aria-label="Loja"
        >
          <option value="">{lojas === null ? "Carregando lojas…" : `Escolha a loja (${lojas.length})`}</option>
          {(lojas || []).map((l) => (
            <option key={l.merchant_id} value={l.merchant_id}>
              {l.loja}
              {l.pausados ? ` · ${l.pausados} parado(s)` : ""}
            </option>
          ))}
        </select>
        {merchantId && (
          <button style={s.botaoSec} onClick={() => escolher("")}>
            Ver todas as lojas
          </button>
        )}
        {loja?.erro && <span style={pilula(cores.vermelho)} title={loja.erro}>última leitura falhou</span>}
      </div>

      {lojas !== null && lojas.length === 0 && (
        <div style={{ ...s.card, ...s.vazio }}>
          Nenhuma loja do iFood ainda. Elas aparecem quando autorizam o aplicativo.
        </div>
      )}

      {merchantId && loja && <CardapioLoja key={merchantId} loja={loja} onMudou={carregarLojas} />}
      {merchantId && lojas !== null && !loja && <div style={s.aviso}>Loja não encontrada.</div>}
      {!merchantId && lojas !== null && lojas.length > 0 && <ParadosAgora onAbrir={escolher} />}
    </div>
  );
}

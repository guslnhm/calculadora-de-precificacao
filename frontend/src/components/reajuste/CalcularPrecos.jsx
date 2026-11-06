import { useCallback, useEffect, useMemo, useState } from "react";
import { reajusteApi } from "../../services/api";
import { cores, pilula, s } from "../monitor/ui";
import { lerPreco, reais } from "../cardapio/Controles";
import { RegrasBar } from "./Regras";

const CUPONS = [
  ["BASE", "Base"],
  ["CUPOM_5", "Cupom 5"],
  ["CUPOM_8", "Cupom 8"],
  ["CUPOM_10", "Cupom 10"],
];

function pct(v) {
  return v == null || Number.isNaN(v) ? "—" : `${Number(v).toFixed(1).replace(".", ",")}%`;
}

function texto(v) {
  return v == null ? "" : Number(v).toFixed(2).replace(".", ",");
}

function iguais(a, b) {
  return a != null && b != null && Math.abs(Number(a) - Number(b)) < 0.005;
}

/** Qual cupom dá exatamente este preço (ou MANUAL se foi digitado). */
function cupomDoPreco(item, preco) {
  const achado = CUPONS.find(([k]) => iguais(item.opcoes?.[k], preco));
  return achado ? achado[0] : "MANUAL";
}

/** Estado inicial de uma linha: o preço salvo (se houver) ou o calculado com o cupom padrão da loja. */
function inicial(item) {
  if (item.precoSalvo != null) {
    return { texto: texto(item.precoSalvo), cupom: cupomDoPreco(item, item.precoSalvo) };
  }
  if (item.semCusto) return { texto: "", cupom: "MANUAL" };
  return { texto: texto(item.opcoes[item.sugerido]), cupom: item.sugerido };
}

/**
 * Preço de cada item: começa no preço salvo; o cupom mostra de onde ele vem (base, cupom 5/8/10 ou manual)
 * e dá para trocar o cupom ou digitar o preço. Só salva na calculadora (não mexe no iFood).
 */
export default function CalcularPrecos({ lojaId, onSalvo }) {
  const [dados, setDados] = useState(null);
  const [linhas, setLinhas] = useState({});
  const [erro, setErro] = useState("");
  const [msg, setMsg] = useState("");
  const [busca, setBusca] = useState("");
  const [soAlterados, setSoAlterados] = useState(false);
  const [salvando, setSalvando] = useState(false);
  const [versaoRegras, setVersaoRegras] = useState(0);

  const carregar = useCallback(async () => {
    try {
      const d = await reajusteApi.precos(lojaId);
      setDados(d);
      setLinhas(Object.fromEntries(d.itens.map((i) => [i.itemId, inicial(i)])));
      setErro("");
    } catch (e) {
      setErro(e.message);
    }
  }, [lojaId]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  const margem = useCallback(
    (item, preco) => {
      if (preco == null || preco <= 0 || item.cmv == null || !dados) return null;
      const frete = Number(item.frete ?? dados.regras.fretePadrao ?? 0);
      const custos = Number(dados.regras.percentualCustos) / 100;
      return ((preco - preco * custos - (Number(item.cmv) + frete)) / preco) * 100;
    },
    [dados]
  );

  const alterados = useMemo(() => {
    if (!dados) return [];
    return dados.itens.filter((i) => {
      const p = lerPreco(linhas[i.itemId]?.texto);
      return p != null && p > 0 && !iguais(p, i.precoSalvo);
    });
  }, [dados, linhas]);

  const visiveis = useMemo(() => {
    const t = busca.trim().toLowerCase();
    const ids = new Set(alterados.map((i) => i.itemId));
    return (dados?.itens || []).filter((i) => (!t || i.nome.toLowerCase().includes(t)) && (!soAlterados || ids.has(i.itemId)));
  }, [dados, busca, soAlterados, alterados]);

  function escolherCupom(item, cupom) {
    setLinhas((l) => ({
      ...l,
      [item.itemId]: cupom === "MANUAL" ? { ...l[item.itemId], cupom } : { texto: texto(item.opcoes[cupom]), cupom },
    }));
  }

  function digitar(item, valor) {
    const p = lerPreco(valor);
    setLinhas((l) => ({ ...l, [item.itemId]: { texto: valor, cupom: p == null ? "MANUAL" : cupomDoPreco(item, p) } }));
  }

  function cupomEmTodos(cupom) {
    setLinhas((l) => {
      const n = { ...l };
      visiveis.filter((i) => !i.semCusto).forEach((i) => (n[i.itemId] = { texto: texto(i.opcoes[cupom]), cupom }));
      return n;
    });
  }

  async function salvar() {
    setSalvando(true);
    setMsg("");
    setErro("");
    try {
      const lista = alterados.map((i) => ({ itemId: i.itemId, preco: lerPreco(linhas[i.itemId].texto) }));
      const r = await reajusteApi.salvarPrecos(lojaId, lista);
      setMsg(`${r.salvos} preço(s) salvo(s) na calculadora. Agora confira em “Revisar e publicar” para mandar ao iFood.`);
      await carregar();
      if (onSalvo) onSalvo();
    } catch (e) {
      setErro(e.message);
    } finally {
      setSalvando(false);
    }
  }

  if (erro && !dados) return <div style={s.erro}>{erro}</div>;
  if (!dados) return <div style={s.vazio}>Calculando…</div>;

  const semCusto = dados.itens.filter((i) => i.semCusto).length;

  return (
    <div>
      <RegrasBar
        key={`${lojaId}-${versaoRegras}`}
        lojaId={lojaId}
        regras={dados.regras}
        onSalvo={() => {
          setVersaoRegras((v) => v + 1);
          carregar();
        }}
      />
      {erro && <div style={s.erro}>{erro}</div>}
      {msg && <div style={s.ok}>{msg}</div>}
      {semCusto > 0 && (
        <div style={s.aviso}>
          {semCusto} item(ns) sem custo: baixe a planilha de custos na página Itens e suba preenchida (ou digite o preço
          direto).
        </div>
      )}

      <div style={{ display: "flex", gap: "10px", flexWrap: "wrap", alignItems: "center", marginBottom: "10px" }}>
        <input style={{ ...s.input, maxWidth: "240px" }} placeholder="Buscar item…" value={busca} onChange={(e) => setBusca(e.target.value)} />
        <label style={s.check}>
          <input type="checkbox" checked={soAlterados} onChange={(e) => setSoAlterados(e.target.checked)} />
          Só os alterados
        </label>
        <span style={{ display: "inline-flex", gap: "6px", alignItems: "center", flexWrap: "wrap" }}>
          <span style={s.suave}>Aplicar em todos os visíveis:</span>
          {CUPONS.map(([k, r]) => (
            <button key={k} style={{ ...s.botaoSec, padding: "5px 9px" }} onClick={() => cupomEmTodos(k)}>
              {r}
            </button>
          ))}
          <button style={{ ...s.botaoSec, padding: "5px 9px" }} onClick={carregar} title="Volta tudo para o preço salvo">
            Desfazer edições
          </button>
        </span>
        <button style={{ ...s.botao, marginLeft: "auto" }} disabled={!alterados.length || salvando} onClick={salvar}>
          {salvando ? "Salvando…" : `Salvar ${alterados.length} alteração(ões)`}
        </button>
      </div>

      <div style={{ ...s.card, padding: "8px 14px" }}>
        {visiveis.length === 0 ? (
          <div style={s.vazio}>
            {dados.itens.length === 0 ? "Nenhum item nesta loja. Importe os itens do iFood na página Itens." : "Nada neste filtro."}
          </div>
        ) : (
          <div style={s.tabelaWrap}>
            <table style={s.tabela}>
              <thead>
                <tr>
                  <th style={s.th}>Item</th>
                  <th style={{ ...s.th, textAlign: "right" }}>Custo</th>
                  <th style={{ ...s.th, textAlign: "right" }}>No iFood</th>
                  <th style={{ ...s.th, textAlign: "right" }}>Salvo</th>
                  <th style={s.th}>Cupom</th>
                  <th style={{ ...s.th, textAlign: "right" }}>Novo preço</th>
                  <th style={{ ...s.th, textAlign: "right" }}>Margem</th>
                </tr>
              </thead>
              <tbody>
                {visiveis.map((i) => {
                  const l = linhas[i.itemId] || { texto: "", cupom: "MANUAL" };
                  const p = lerPreco(l.texto);
                  const invalido = l.texto !== "" && (p == null || p <= 0);
                  const mudou = p != null && p > 0 && !iguais(p, i.precoSalvo);
                  return (
                    <tr key={i.itemId} style={{ background: mudou ? "rgba(230,57,70,0.07)" : "transparent" }}>
                      <td style={s.td}>
                        {i.nome}
                        {mudou && <div style={{ color: cores.destaque, fontSize: "12px" }}>alterado, falta salvar</div>}
                      </td>
                      <td style={{ ...s.td, textAlign: "right", whiteSpace: "nowrap" }}>
                        {i.semCusto ? <span style={pilula(cores.amarelo)}>sem custo</span> : reais(i.cmv)}
                      </td>
                      <td style={{ ...s.td, textAlign: "right", whiteSpace: "nowrap" }}>{reais(i.precoIfood)}</td>
                      <td style={{ ...s.td, textAlign: "right", whiteSpace: "nowrap" }}>{reais(i.precoSalvo)}</td>
                      <td style={s.td}>
                        <select
                          style={{ ...s.input, width: "auto", padding: "6px 8px" }}
                          value={l.cupom}
                          onChange={(e) => escolherCupom(i, e.target.value)}
                          aria-label={`Cupom de ${i.nome}`}
                        >
                          {!i.semCusto &&
                            CUPONS.map(([k, r]) => (
                              <option key={k} value={k}>
                                {r} · {reais(i.opcoes[k])}
                              </option>
                            ))}
                          <option value="MANUAL">Manual (digitado)</option>
                        </select>
                      </td>
                      <td style={{ ...s.td, textAlign: "right" }}>
                        <input
                          inputMode="decimal"
                          value={l.texto}
                          onChange={(e) => digitar(i, e.target.value)}
                          placeholder="0,00"
                          aria-label={`Novo preço de ${i.nome}`}
                          style={{
                            ...s.input,
                            width: "100px",
                            padding: "6px 8px",
                            textAlign: "right",
                            fontWeight: "bold",
                            borderColor: invalido ? cores.vermelho : mudou ? cores.destaque : cores.bordaCampo,
                          }}
                        />
                      </td>
                      <td style={{ ...s.td, textAlign: "right", whiteSpace: "nowrap" }}>{i.semCusto ? "—" : pct(margem(i, p))}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
      <p style={{ ...s.suave, marginTop: "10px" }}>
        Salvar aqui só grava o preço na calculadora (o mesmo “preço de venda iFood” da Calculadora iFood). Nada muda no
        iFood até você publicar em “Revisar e publicar”.
      </p>
    </div>
  );
}

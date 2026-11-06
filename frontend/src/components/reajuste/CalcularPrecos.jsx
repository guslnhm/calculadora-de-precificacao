import { useCallback, useEffect, useMemo, useState } from "react";
import { reajusteApi } from "../../services/api";
import { cores, pilula, s } from "../monitor/ui";
import { reais } from "../cardapio/Controles";
import { ARREDONDAMENTOS } from "./LojasERegras";

const MODOS = [
  ["BASE", "Base"],
  ["CUPOM_5", "+ R$ 5"],
  ["CUPOM_8", "+ R$ 8"],
  ["CUPOM_10", "+ R$ 10"],
];

function pct(v) {
  return v == null ? "—" : `${Number(v).toFixed(1).replace(".", ",")}%`;
}

/** Calcula o preço de todos os itens pela calculadora, escolhendo o cupom item a item, e salva. */
export default function CalcularPrecos({ lojaId, onSalvo }) {
  const [dados, setDados] = useState(null);
  const [erro, setErro] = useState("");
  const [msg, setMsg] = useState("");
  const [modo, setModo] = useState({});
  const [marcados, setMarcados] = useState(new Set());
  const [busca, setBusca] = useState("");
  const [soMudam, setSoMudam] = useState(false);
  const [salvando, setSalvando] = useState(false);

  const carregar = useCallback(async () => {
    try {
      const d = await reajusteApi.precos(lojaId);
      setDados(d);
      setModo(Object.fromEntries(d.itens.map((i) => [i.itemId, i.sugerido])));
      setMarcados(new Set());
      setErro("");
    } catch (e) {
      setErro(e.message);
    }
  }, [lojaId]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  const precoEscolhido = useCallback((i) => (i.semCusto ? null : i.opcoes[modo[i.itemId] || i.sugerido]), [modo]);

  const visiveis = useMemo(() => {
    const t = busca.trim().toLowerCase();
    return (dados?.itens || []).filter((i) => {
      if (t && !i.nome.toLowerCase().includes(t)) return false;
      if (soMudam) {
        const p = precoEscolhido(i);
        if (p == null || (i.precoSalvo != null && Number(p) === Number(i.precoSalvo))) return false;
      }
      return true;
    });
  }, [dados, busca, soMudam, precoEscolhido]);

  const semCusto = (dados?.itens || []).filter((i) => i.semCusto).length;

  function alternar(id) {
    setMarcados((m) => {
      const n = new Set(m);
      if (n.has(id)) n.delete(id);
      else n.add(id);
      return n;
    });
  }

  function marcarVisiveis(marcar) {
    setMarcados((m) => {
      const n = new Set(m);
      visiveis.filter((i) => !i.semCusto).forEach((i) => (marcar ? n.add(i.itemId) : n.delete(i.itemId)));
      return n;
    });
  }

  function cupomParaMarcados(novo) {
    setModo((m) => {
      const n = { ...m };
      marcados.forEach((id) => (n[id] = novo));
      return n;
    });
  }

  async function salvar() {
    setSalvando(true);
    setMsg("");
    try {
      const lista = dados.itens
        .filter((i) => marcados.has(i.itemId) && !i.semCusto)
        .map((i) => ({ itemId: i.itemId, preco: precoEscolhido(i) }));
      const r = await reajusteApi.salvarPrecos(lojaId, lista);
      setMsg(`${r.salvos} preço(s) salvo(s) na calculadora. Confira em “3. Revisar e publicar” antes de mandar para o iFood.`);
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

  return (
    <div>
      <p style={{ ...s.suave, marginTop: 0 }}>
        Preço = (custo + frete) ÷ (1 − {pct(dados.regras.percentualTotal)} dos percentuais da loja), mais o cupom
        escolhido em cada item · {ARREDONDAMENTOS[dados.regras.arredondamento]}. Salvar aqui <strong>não</strong> muda
        nada no iFood: só grava o “preço de venda iFood” na calculadora. Para um item só, também dá para usar a
        Calculadora iFood como antes.
      </p>
      {erro && <div style={s.erro}>{erro}</div>}
      {msg && <div style={s.ok}>{msg}</div>}
      {semCusto > 0 && (
        <div style={s.aviso}>
          {semCusto} item(ns) sem custo: preencha pela planilha de custos (aba “1. Lojas e regras”) para calcular.
        </div>
      )}

      <div style={{ display: "flex", gap: "10px", flexWrap: "wrap", alignItems: "center", marginBottom: "10px" }}>
        <input style={{ ...s.input, maxWidth: "260px" }} placeholder="Buscar item…" value={busca} onChange={(e) => setBusca(e.target.value)} />
        <label style={s.check}>
          <input type="checkbox" checked={soMudam} onChange={(e) => setSoMudam(e.target.checked)} />
          Só os que mudam
        </label>
        <button style={s.botaoSec} onClick={() => marcarVisiveis(true)}>
          Marcar visíveis
        </button>
        <button style={s.botaoSec} onClick={() => marcarVisiveis(false)}>
          Desmarcar
        </button>
        {marcados.size > 0 && (
          <span style={{ display: "inline-flex", gap: "6px", alignItems: "center" }}>
            <span style={s.suave}>Cupom nos marcados:</span>
            {MODOS.map(([k, r]) => (
              <button key={k} style={{ ...s.botaoSec, padding: "5px 9px" }} onClick={() => cupomParaMarcados(k)}>
                {r}
              </button>
            ))}
          </span>
        )}
        <button style={{ ...s.botao, marginLeft: "auto" }} disabled={!marcados.size || salvando} onClick={salvar}>
          {salvando ? "Salvando…" : `Salvar ${marcados.size} preço(s) na calculadora`}
        </button>
      </div>

      <div style={{ ...s.card, padding: "8px 14px" }}>
        {visiveis.length === 0 ? (
          <div style={s.vazio}>
            {dados.itens.length === 0 ? "Nenhum item nesta loja. Importe os itens do iFood na aba 1." : "Nada neste filtro."}
          </div>
        ) : (
          <div style={s.tabelaWrap}>
            <table style={s.tabela}>
              <thead>
                <tr>
                  <th style={s.th}></th>
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
                  const m = modo[i.itemId] || i.sugerido;
                  const p = precoEscolhido(i);
                  const igual = p != null && i.precoSalvo != null && Number(p) === Number(i.precoSalvo);
                  return (
                    <tr key={i.itemId} style={{ opacity: i.semCusto ? 0.55 : 1 }}>
                      <td style={s.td}>
                        <input
                          type="checkbox"
                          disabled={i.semCusto}
                          checked={marcados.has(i.itemId)}
                          onChange={() => alternar(i.itemId)}
                          aria-label={`Salvar ${i.nome}`}
                        />
                      </td>
                      <td style={s.td}>{i.nome}</td>
                      <td style={{ ...s.td, textAlign: "right", whiteSpace: "nowrap" }}>
                        {i.semCusto ? <span style={pilula(cores.amarelo)}>sem custo</span> : reais(i.cmv)}
                      </td>
                      <td style={{ ...s.td, textAlign: "right", whiteSpace: "nowrap" }}>{reais(i.precoIfood)}</td>
                      <td style={{ ...s.td, textAlign: "right", whiteSpace: "nowrap" }}>{reais(i.precoSalvo)}</td>
                      <td style={s.td}>
                        {!i.semCusto && (
                          <select
                            style={{ ...s.input, width: "auto", padding: "6px 8px" }}
                            value={m}
                            onChange={(e) => {
                              const v = e.target.value;
                              setModo((x) => ({ ...x, [i.itemId]: v }));
                              setMarcados((x) => new Set(x).add(i.itemId));
                            }}
                            aria-label={`Cupom de ${i.nome}`}
                          >
                            {MODOS.map(([k, r]) => (
                              <option key={k} value={k}>
                                {r} · {reais(i.opcoes[k])}
                              </option>
                            ))}
                          </select>
                        )}
                      </td>
                      <td style={{ ...s.td, textAlign: "right", whiteSpace: "nowrap", fontWeight: "bold" }}>
                        {p == null ? "—" : reais(p)}
                        {igual && <div style={{ ...s.suave, fontWeight: "normal" }}>já salvo</div>}
                      </td>
                      <td style={{ ...s.td, textAlign: "right", whiteSpace: "nowrap" }}>{pct(i.margens?.[m])}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

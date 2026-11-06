import { useCallback, useEffect, useMemo, useState } from "react";
import { reajusteApi } from "../../services/api";
import { cores, s } from "../monitor/ui";
import { reais } from "../cardapio/Controles";
import { ARREDONDAMENTOS, MODOS } from "./Regras";
import Progresso from "./Progresso";

function pct(v, sinal = false) {
  if (v === null || v === undefined) return "—";
  const n = Number(v);
  return `${sinal && n > 0 ? "+" : ""}${n.toFixed(1).replace(".", ",")}%`;
}

export default function Previa({ lojaId, onPublicado }) {
  const fonte = "SALVO";
  const [previa, setPrevia] = useState(null);
  const [erro, setErro] = useState("");
  const [selecionadas, setSelecionadas] = useState(new Set());
  const [soMudancas, setSoMudancas] = useState(true);
  const [busca, setBusca] = useState("");
  const [confirmar, setConfirmar] = useState(false);
  const [publicando, setPublicando] = useState(false);
  const [lote, setLote] = useState(null);

  const carregar = useCallback(async () => {
    setPrevia(null);
    try {
      const p = await reajusteApi.previa(lojaId, fonte);
      setPrevia(p);
      setSelecionadas(new Set(p.linhas.filter((l) => l.muda).map((l) => l.chave)));
      setErro("");
    } catch (e) {
      setErro(e.message);
    }
  }, [lojaId, fonte]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  const lojasDiferentes = useMemo(() => new Set((previa?.linhas || []).map((l) => l.merchantId)).size, [previa]);

  const visiveis = useMemo(() => {
    const t = busca.trim().toLowerCase();
    return (previa?.linhas || []).filter(
      (l) =>
        (!soMudancas || l.muda || l.aviso) &&
        (!t || `${l.ifoodNome} ${l.itemNome} ${l.categoria} ${l.loja}`.toLowerCase().includes(t))
    );
  }, [previa, soMudancas, busca]);

  const resumo = useMemo(() => {
    const escolhidas = (previa?.linhas || []).filter((l) => selecionadas.has(l.chave) && l.muda && l.precoNovo != null);
    const variacoes = escolhidas.map((l) => Number(l.variacao)).filter((v) => !Number.isNaN(v));
    const media = variacoes.length ? variacoes.reduce((a, b) => a + b, 0) / variacoes.length : null;
    const sobem = escolhidas.filter((l) => l.precoAtual == null || Number(l.precoNovo) > Number(l.precoAtual)).length;
    return { n: escolhidas.length, media, sobem, descem: escolhidas.length - sobem, max: variacoes.length ? Math.max(...variacoes) : null };
  }, [previa, selecionadas]);

  function alternar(chave) {
    setSelecionadas((sel) => {
      const n = new Set(sel);
      if (n.has(chave)) n.delete(chave);
      else n.add(chave);
      return n;
    });
  }

  function marcarVisiveis(marcar) {
    setSelecionadas((sel) => {
      const n = new Set(sel);
      visiveis.filter((l) => l.muda && l.precoNovo != null).forEach((l) => (marcar ? n.add(l.chave) : n.delete(l.chave)));
      return n;
    });
  }

  async function publicar() {
    setPublicando(true);
    setErro("");
    try {
      const chaves = previa.linhas.filter((l) => selecionadas.has(l.chave) && l.muda && l.precoNovo != null).map((l) => l.chave);
      const r = await reajusteApi.publicar(lojaId, fonte, chaves);
      setLote(r);
      setConfirmar(false);
    } catch (e) {
      setErro(e.message);
    } finally {
      setPublicando(false);
    }
  }

  if (lote) {
    return (
      <Progresso
        loteId={lote.id}
        onFechar={() => {
          setLote(null);
          carregar();
          if (onPublicado) onPublicado();
        }}
      />
    );
  }

  return (
    <div>
      <div style={{ ...s.card, padding: "14px 18px", marginBottom: "14px", display: "grid", gap: "10px" }}>
        <div style={{ fontSize: "14px" }}>
          Compara o <strong>preço de venda iFood salvo na calculadora</strong> com o preço que está hoje no iFood.
          Para mudar um preço, volte em “2. Calcular preços” (ou na Calculadora iFood).
        </div>
        {previa && (
          <div style={s.suave}>
            Regras: {MODOS[previa.regras.modoCupom]} · {ARREDONDAMENTOS[previa.regras.arredondamento]} · percentuais da
            loja somam {pct(previa.regras.percentualTotal)}
          </div>
        )}
      </div>

      {erro && <div style={s.erro}>{erro}</div>}
      {previa?.avisos?.map((a) => (
        <div key={a} style={s.aviso}>
          {a}
        </div>
      ))}
      {!previa && !erro && <div style={s.vazio}>Calculando a prévia…</div>}

      {previa && (
        <>
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))",
              gap: "10px",
              marginBottom: "14px",
            }}
          >
            {[
              ["Preços a mudar", resumo.n],
              ["Sobem / descem", `${resumo.sobem} / ${resumo.descem}`],
              ["Variação média", pct(resumo.media, true)],
              ["Maior aumento", pct(resumo.max, true)],
            ].map(([r, v]) => (
              <div key={r} style={{ ...s.card, padding: "12px 16px" }}>
                <div style={{ fontSize: "22px", fontWeight: "bold" }}>{v}</div>
                <div style={s.suave}>{r}</div>
              </div>
            ))}
          </div>

          <div style={{ display: "flex", gap: "10px", flexWrap: "wrap", alignItems: "center", marginBottom: "10px" }}>
            <input style={{ ...s.input, maxWidth: "280px" }} placeholder="Buscar…" value={busca} onChange={(e) => setBusca(e.target.value)} />
            <label style={s.check}>
              <input type="checkbox" checked={soMudancas} onChange={(e) => setSoMudancas(e.target.checked)} />
              Só o que muda
            </label>
            <button style={s.botaoSec} onClick={() => marcarVisiveis(true)}>
              Marcar visíveis
            </button>
            <button style={s.botaoSec} onClick={() => marcarVisiveis(false)}>
              Desmarcar visíveis
            </button>
            <span style={{ marginLeft: "auto" }}>
              {!confirmar ? (
                <button style={s.botao} disabled={!resumo.n} onClick={() => setConfirmar(true)}>
                  Publicar {resumo.n} preço(s) no iFood
                </button>
              ) : (
                <span style={{ display: "inline-flex", gap: "8px", alignItems: "center", flexWrap: "wrap" }}>
                  <span style={{ color: cores.amarelo, fontSize: "14px" }}>
                    Vai mudar {resumo.n} preço(s) no iFood agora. Confirma?
                  </span>
                  <button style={s.botao} onClick={publicar} disabled={publicando}>
                    {publicando ? "Enviando…" : "Sim, publicar"}
                  </button>
                  <button style={s.botaoSec} onClick={() => setConfirmar(false)}>
                    Cancelar
                  </button>
                </span>
              )}
            </span>
          </div>

          <div style={{ ...s.card, padding: "8px 14px" }}>
            {visiveis.length === 0 ? (
              <div style={s.vazio}>
                {previa.linhas.length === 0
                  ? "Nenhum item ligado ainda. Ligue os itens na aba “Vínculos dos itens”."
                  : "Nenhum preço muda: o iFood já está com os preços da calculadora. ✅"}
              </div>
            ) : (
              <div style={s.tabelaWrap}>
                <table style={s.tabela}>
                  <thead>
                    <tr>
                      <th style={s.th}></th>
                      {lojasDiferentes > 1 && <th style={s.th}>Loja</th>}
                      <th style={s.th}>Item no iFood</th>
                      <th style={s.th}>Calculadora</th>
                      <th style={{ ...s.th, textAlign: "right" }}>Atual</th>
                      <th style={{ ...s.th, textAlign: "right" }}>Novo</th>
                      <th style={{ ...s.th, textAlign: "right" }}>Variação</th>
                      <th style={{ ...s.th, textAlign: "right" }}>Margem</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visiveis.map((l) => {
                      const pode = l.muda && l.precoNovo != null;
                      const sobe = pode && Number(l.precoNovo) > Number(l.precoAtual);
                      return (
                        <tr key={l.chave} style={{ opacity: pode ? 1 : 0.6 }}>
                          <td style={s.td}>
                            <input
                              type="checkbox"
                              disabled={!pode}
                              checked={pode && selecionadas.has(l.chave)}
                              onChange={() => alternar(l.chave)}
                              aria-label={`Publicar ${l.ifoodNome}`}
                            />
                          </td>
                          {lojasDiferentes > 1 && <td style={s.td}>{l.loja}</td>}
                          <td style={s.td}>
                            <div style={{ fontWeight: "bold" }}>{l.ifoodNome}</div>
                            <div style={s.suave}>
                              {l.tipo === "COMPLEMENTO" ? "complemento · " : ""}
                              {l.categoria}
                              {l.status === "UNAVAILABLE" && " · pausado"}
                            </div>
                          </td>
                          <td style={s.td}>
                            {l.itemNome}
                            <div style={s.suave}>CMV {reais(l.cmv)}</div>
                          </td>
                          <td style={{ ...s.td, textAlign: "right", whiteSpace: "nowrap" }}>{reais(l.precoAtual)}</td>
                          <td style={{ ...s.td, textAlign: "right", whiteSpace: "nowrap", fontWeight: "bold" }}>
                            {l.precoNovo != null ? reais(l.precoNovo) : "—"}
                            {l.aviso && <div style={{ color: cores.amarelo, fontSize: "12px", fontWeight: "normal" }}>{l.aviso}</div>}
                          </td>
                          <td
                            style={{
                              ...s.td,
                              textAlign: "right",
                              whiteSpace: "nowrap",
                              color: !l.muda ? cores.textoSuave : sobe ? cores.amarelo : cores.azul,
                            }}
                          >
                            {l.muda ? pct(l.variacao, true) : "igual"}
                          </td>
                          <td style={{ ...s.td, textAlign: "right", whiteSpace: "nowrap" }}>
                            {pct(l.margemAtual)}
                            {l.muda && l.margemNova != null && <> → {pct(l.margemNova)}</>}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
          <p style={{ ...s.suave, marginTop: "10px" }}>
            Margem = lucro sobre o preço, com os percentuais da loja (como na calculadora reversa). Publicar não gera
            alerta nos grupos e tudo pode ser desfeito no histórico.
          </p>
        </>
      )}
    </div>
  );
}

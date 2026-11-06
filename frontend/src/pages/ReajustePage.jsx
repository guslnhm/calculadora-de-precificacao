import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { listarLojas, reajusteApi } from "../services/api";
import { cores, dataHora, pilula, s } from "../components/monitor/ui";
import LojasERegras from "../components/reajuste/LojasERegras";
import Vinculos from "../components/reajuste/Vinculos";
import Previa from "../components/reajuste/Previa";
import Progresso, { SITUACOES_LOTE } from "../components/reajuste/Progresso";

const ABAS = [
  { id: "lojas", rotulo: "1. Lojas e regras" },
  { id: "vinculos", rotulo: "2. Vínculos dos itens" },
  { id: "previa", rotulo: "3. Prévia e publicar" },
  { id: "historico", rotulo: "Histórico" },
];

function Historico({ lojaId }) {
  const [lotes, setLotes] = useState(null);
  const [aberto, setAberto] = useState(null);
  const [erro, setErro] = useState("");
  const [versao, setVersao] = useState(0);

  useEffect(() => {
    reajusteApi
      .lotes(lojaId)
      .then(setLotes)
      .catch((e) => setErro(e.message));
  }, [lojaId, versao]);

  if (aberto) {
    return (
      <Progresso
        loteId={aberto}
        onFechar={() => {
          setAberto(null);
          setVersao((v) => v + 1);
        }}
      />
    );
  }

  return (
    <div style={s.card}>
      {erro && <div style={s.erro}>{erro}</div>}
      {lotes === null && !erro && <div style={s.suave}>Carregando…</div>}
      {lotes !== null && lotes.length === 0 && <div style={s.vazio}>Nenhum reajuste publicado para esta loja ainda.</div>}
      {lotes !== null && lotes.length > 0 && (
        <div style={s.tabelaWrap}>
          <table style={s.tabela}>
            <thead>
              <tr>
                <th style={s.th}>#</th>
                <th style={s.th}>Quando</th>
                <th style={s.th}>Quem</th>
                <th style={s.th}>Preços</th>
                <th style={s.th}>Situação</th>
                <th style={s.th}></th>
              </tr>
            </thead>
            <tbody>
              {lotes.map((l) => {
                const sit = SITUACOES_LOTE[l.situacao] || { rotulo: l.situacao, cor: cores.cinza };
                return (
                  <tr key={l.id}>
                    <td style={s.td}>{l.id}</td>
                    <td style={{ ...s.td, whiteSpace: "nowrap" }}>{dataHora(l.criadoEm)}</td>
                    <td style={s.td}>{l.usuario || "—"}</td>
                    <td style={s.td}>
                      {l.feitas} de {l.total}
                      {l.falhas ? <span style={{ color: cores.amarelo }}> · {l.falhas} falha(s)</span> : ""}
                      <div style={s.suave}>{l.fonte === "SALVO" ? "preço salvo na calculadora" : "calculado pela calculadora"}</div>
                    </td>
                    <td style={s.td}>
                      <span style={pilula(sit.cor)}>{sit.rotulo}</span>
                      {l.desfeitoEm && (
                        <div style={s.suave}>
                          {dataHora(l.desfeitoEm)}
                          {l.desfeitoPor ? ` por ${l.desfeitoPor}` : ""}
                        </div>
                      )}
                    </td>
                    <td style={{ ...s.td, textAlign: "right" }}>
                      <button style={{ ...s.botaoSec, padding: "5px 10px" }} onClick={() => setAberto(l.id)}>
                        Ver
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default function ReajustePage() {
  const [params, setParams] = useSearchParams();
  const lojaId = params.get("loja") ? Number(params.get("loja")) : null;
  const aba = ABAS.some((a) => a.id === params.get("aba")) ? params.get("aba") : "lojas";
  const [lojas, setLojas] = useState(null);
  const [dados, setDados] = useState(null);
  const [erro, setErro] = useState("");
  const [versao, setVersao] = useState(0);

  useEffect(() => {
    listarLojas("IFOOD")
      .then(setLojas)
      .catch((e) => setErro(e.message));
  }, []);

  const carregar = useCallback(async () => {
    if (!lojaId) return;
    try {
      setDados(await reajusteApi.loja(lojaId));
      setErro("");
    } catch (e) {
      setErro(e.message);
    }
  }, [lojaId]);

  useEffect(() => {
    setDados(null);
    carregar();
  }, [carregar]);

  function ir(novaAba, novaLoja = lojaId) {
    const p = {};
    if (novaLoja) p.loja = String(novaLoja);
    if (novaAba) p.aba = novaAba;
    setParams(p);
  }

  return (
    <div style={s.page}>
      <div style={s.header}>
        <div>
          <h2 style={s.titulo}>Reajuste iFood</h2>
          <p style={s.subtitulo}>
            Leva para o iFood os preços da calculadora: depois de atualizar os custos (planilha) ou os percentuais da
            loja, confira a prévia e publique tudo de uma vez.
          </p>
        </div>
      </div>

      {erro && <div style={s.erro}>{erro}</div>}

      <div style={{ marginBottom: "16px", maxWidth: "420px" }}>
        <select style={s.input} value={lojaId || ""} onChange={(e) => ir("lojas", e.target.value || null)} aria-label="Loja da calculadora">
          <option value="">{lojas === null ? "Carregando lojas…" : "Escolha a loja da calculadora"}</option>
          {(lojas || []).map((l) => (
            <option key={l.id} value={l.id}>
              {l.nome}
              {l.clienteNome ? ` · ${l.clienteNome}` : ""}
            </option>
          ))}
        </select>
      </div>

      {!lojaId && (
        <div style={{ ...s.card, display: "grid", gap: "8px", lineHeight: 1.6 }}>
          <strong>Como funciona</strong>
          <span>1. Escolha a loja da calculadora, diga quais lojas do iFood usam os preços dela e a regra de cupom.</span>
          <span>2. Ligue cada item do iFood ao item da calculadora (o sistema sugere pelos nomes).</span>
          <span>3. Confira a prévia (preço atual × novo e a margem) e publique. Dá para desfazer tudo depois.</span>
        </div>
      )}

      {lojaId && dados && (
        <>
          <div style={s.abas}>
            {ABAS.map((a) => (
              <button key={a.id} style={{ ...s.aba, ...(aba === a.id ? s.abaAtiva : {}) }} onClick={() => ir(a.id)}>
                {a.rotulo}
              </button>
            ))}
          </div>
          {aba === "lojas" && (
            <LojasERegras
              key={`${lojaId}-${versao}`}
              lojaId={lojaId}
              dados={dados}
              onSalvo={() => {
                carregar();
                setVersao((v) => v + 1);
              }}
            />
          )}
          {aba === "vinculos" && <Vinculos key={`${lojaId}-${versao}`} lojaId={lojaId} lojasIfood={dados.lojasIfood} />}
          {aba === "previa" && <Previa key={lojaId} lojaId={lojaId} onPublicado={() => setVersao((v) => v + 1)} />}
          {aba === "historico" && <Historico key={`${lojaId}-${versao}`} lojaId={lojaId} />}
        </>
      )}
      {lojaId && !dados && !erro && <div style={s.vazio}>Carregando…</div>}
    </div>
  );
}

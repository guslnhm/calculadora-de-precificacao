import { Fragment, useCallback, useEffect, useMemo, useState } from "react";
import { monitorApi } from "../../services/api";
import {
  cores,
  dataHora,
  desdeAgora,
  detalheMotivos,
  diaISO,
  estadoInfo,
  hora,
  pilula,
  s,
  textoMotivos,
  useTelaEstreita,
} from "./ui";

const ATUALIZAR_A_CADA_MS = 30000;

function Contador({ rotulo, valor, cor, ativo, onClick }) {
  return (
    <button
      onClick={onClick}
      style={{
        ...s.card,
        padding: "14px 16px",
        textAlign: "left",
        cursor: "pointer",
        color: cores.texto,
        borderColor: ativo ? cor : cores.borda,
        boxShadow: ativo ? `0 0 0 1px ${cor}` : "none",
      }}
    >
      <div style={{ fontSize: "26px", fontWeight: "bold", color: valor ? cor : cores.textoSuave }}>{valor}</div>
      <div style={s.suave}>{rotulo}</div>
    </button>
  );
}

function Historico({ merchantId }) {
  const [linhas, setLinhas] = useState(null);
  const [erro, setErro] = useState("");
  const hoje = diaISO();

  useEffect(() => {
    monitorApi
      .historico(merchantId, hoje, hoje)
      .then(setLinhas)
      .catch((e) => setErro(e.message));
  }, [merchantId, hoje]);

  if (erro) return <div style={s.erro}>{erro}</div>;
  if (!linhas) return <div style={s.suave}>Carregando histórico…</div>;
  if (!linhas.length) return <div style={s.suave}>Nenhuma mudança de status hoje.</div>;

  return (
    <div>
      <div style={{ ...s.suave, marginBottom: "8px" }}>Mudanças de status hoje</div>
      <div style={{ display: "grid", gap: "6px" }}>
        {linhas.map((h, i) => {
          const info = estadoInfo(h.estado);
          return (
            <div key={i} style={{ display: "flex", gap: "10px", alignItems: "baseline", flexWrap: "wrap" }}>
              <span style={{ fontVariantNumeric: "tabular-nums", minWidth: "44px" }}>{hora(h.mudou_em)}</span>
              <span style={pilula(info.cor)}>{info.rotulo}</span>
              <span style={{ color: cores.textoSuave, fontSize: "13px" }}>
                {textoMotivos(h.motivos, h.estado) || (h.estado === "OK" ? "" : h.mensagem)}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

export default function AbaLojas({ versao }) {
  const [linhas, setLinhas] = useState(null);
  const [erro, setErro] = useState("");
  const [busca, setBusca] = useState("");
  const [filtro, setFiltro] = useState("PROBLEMAS");
  const [aberta, setAberta] = useState(null);
  const [agora, setAgora] = useState(() => Date.now());
  const estreita = useTelaEstreita();

  const carregar = useCallback(async () => {
    try {
      const dados = await monitorApi.painel();
      setLinhas(dados);
      setErro("");
      setAgora(Date.now());
    } catch (e) {
      setErro(e.message);
    }
  }, []);

  useEffect(() => {
    carregar();
    const t = setInterval(carregar, ATUALIZAR_A_CADA_MS);
    return () => clearInterval(t);
  }, [carregar, versao]);

  const contagem = useMemo(() => {
    const c = { ERROR: 0, PAUSED: 0, WARNING: 0, CLOSED: 0, OK: 0 };
    (linhas || []).forEach((l) => {
      c[l.estado] = (c[l.estado] || 0) + 1;
    });
    return c;
  }, [linhas]);

  const multiOperacao = useMemo(() => {
    const vistos = {};
    (linhas || []).forEach((l) => {
      vistos[l.merchant_id] = (vistos[l.merchant_id] || 0) + 1;
    });
    return vistos;
  }, [linhas]);

  const visiveis = useMemo(() => {
    const termo = busca.trim().toLowerCase();
    return (linhas || []).filter((l) => {
      if (filtro === "PROBLEMAS" && !["ERROR", "PAUSED", "WARNING"].includes(l.estado)) return false;
      if (filtro !== "TODAS" && filtro !== "PROBLEMAS" && l.estado !== filtro) return false;
      return !termo || String(l.loja).toLowerCase().includes(termo);
    });
  }, [linhas, busca, filtro]);

  const ultimaLeitura = useMemo(() => {
    const ts = (linhas || []).map((l) => new Date(l.verificado_em).getTime()).filter((x) => !Number.isNaN(x));
    return ts.length ? Math.max(...ts) : null;
  }, [linhas]);

  const problemas = contagem.ERROR + contagem.PAUSED + contagem.WARNING;
  const atrasado = ultimaLeitura && agora - ultimaLeitura > 3 * 60 * 1000;

  return (
    <div>
      {erro && <div style={s.erro}>Não consegui carregar o painel: {erro}</div>}
      {atrasado && (
        <div style={s.aviso}>
          A última leitura foi há {desdeAgora(ultimaLeitura, agora)}. O monitor pode estar desligado ou sem
          acesso ao iFood.
        </div>
      )}

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(130px, 1fr))",
          gap: "10px",
          marginBottom: "16px",
        }}
      >
        <Contador
          rotulo="Com problema"
          valor={problemas}
          cor={cores.destaque}
          ativo={filtro === "PROBLEMAS"}
          onClick={() => setFiltro("PROBLEMAS")}
        />
        {["ERROR", "PAUSED", "WARNING", "CLOSED", "OK"].map((e) => (
          <Contador
            key={e}
            rotulo={estadoInfo(e).rotulo}
            valor={contagem[e]}
            cor={estadoInfo(e).cor}
            ativo={filtro === e}
            onClick={() => setFiltro(e)}
          />
        ))}
      </div>

      <div style={s.card}>
        <div style={{ display: "flex", gap: "10px", flexWrap: "wrap", marginBottom: "12px", alignItems: "center" }}>
          <input
            style={{ ...s.input, maxWidth: "320px" }}
            placeholder="Buscar loja…"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
          />
          <button style={s.botaoSec} onClick={() => setFiltro("TODAS")} disabled={filtro === "TODAS"}>
            Mostrar todas ({(linhas || []).length})
          </button>
          <span style={{ ...s.suave, marginLeft: "auto" }}>
            {ultimaLeitura ? `Última leitura às ${hora(ultimaLeitura)} · atualiza sozinho` : ""}
          </span>
        </div>

        {linhas === null && !erro && <div style={s.vazio}>Carregando…</div>}
        {linhas !== null && linhas.length === 0 && (
          <div style={s.vazio}>
            Nenhuma loja lida ainda. Confira se o monitor está ligado e se as lojas autorizaram o aplicativo.
          </div>
        )}
        {linhas !== null && linhas.length > 0 && visiveis.length === 0 && (
          <div style={s.vazio}>
            {filtro === "PROBLEMAS" ? "Nenhuma loja com problema agora. ✅" : "Nenhuma loja neste filtro."}
          </div>
        )}

        {visiveis.length > 0 && estreita && (
          <div style={{ display: "grid", gap: "8px" }}>
            {visiveis.map((l) => {
              const chave = `${l.merchant_id}|${l.operacao}|${l.canal}`;
              const info = estadoInfo(l.estado);
              const motivo = l.estado === "OK" ? "" : textoMotivos(l.motivos, l.estado);
              return (
                <div
                  key={chave}
                  onClick={() => setAberta(aberta === chave ? null : chave)}
                  style={{
                    border: `1px solid ${cores.borda}`,
                    borderLeft: `3px solid ${info.cor}`,
                    borderRadius: "10px",
                    padding: "10px 12px",
                    cursor: "pointer",
                  }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", gap: "8px", alignItems: "center" }}>
                    <strong>{l.loja}</strong>
                    <span style={pilula(info.cor)}>{info.rotulo}</span>
                  </div>
                  <div style={{ ...s.suave, marginTop: "4px" }}>
                    {motivo ? `${motivo} · ` : ""}desde {hora(l.desde)} (há {desdeAgora(l.desde, agora)})
                  </div>
                  {aberta === chave && (
                    <div style={{ marginTop: "10px", borderTop: `1px solid ${cores.borda}`, paddingTop: "10px" }}>
                      <Historico merchantId={l.merchant_id} />
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}

        {visiveis.length > 0 && !estreita && (
          <div style={s.tabelaWrap}>
            <table style={s.tabela}>
              <thead>
                <tr>
                  <th style={s.th}>Loja</th>
                  <th style={s.th}>Status</th>
                  <th style={s.th}>Motivo</th>
                  <th style={s.th}>Desde</th>
                </tr>
              </thead>
              <tbody>
                {visiveis.map((l) => {
                  const chave = `${l.merchant_id}|${l.operacao}|${l.canal}`;
                  const info = estadoInfo(l.estado);
                  const motivo = l.estado === "OK" ? "" : textoMotivos(l.motivos, l.estado);
                  return (
                    <Fragment key={chave}>
                      <tr
                        style={{ cursor: "pointer", backgroundColor: aberta === chave ? "#1B222B" : "transparent" }}
                        onClick={() => setAberta(aberta === chave ? null : chave)}
                      >
                        <td style={s.td}>
                          <div style={{ fontWeight: "bold" }}>{l.loja}</div>
                          {multiOperacao[l.merchant_id] > 1 && (
                            <div style={s.suave}>
                              {l.operacao} · {l.canal}
                            </div>
                          )}
                        </td>
                        <td style={s.td}>
                          <span style={pilula(info.cor)}>{info.rotulo}</span>
                        </td>
                        <td style={{ ...s.td, color: cores.textoSuave }} title={detalheMotivos(l.motivos)}>
                          {motivo || (l.estado === "OK" ? "—" : l.mensagem || "—")}
                        </td>
                        <td style={{ ...s.td, whiteSpace: "nowrap" }}>
                          {dataHora(l.desde)}
                          <div style={s.suave}>há {desdeAgora(l.desde, agora)}</div>
                        </td>
                      </tr>
                      {aberta === chave && (
                        <tr>
                          <td colSpan={4} style={{ ...s.td, backgroundColor: "#1B222B" }}>
                            {l.mensagem && l.estado !== "OK" && (
                              <div style={{ marginBottom: "10px", fontSize: "13px" }}>
                                <span style={s.suave}>Mensagem do iFood: </span>
                                {l.mensagem}
                              </div>
                            )}
                            <Historico merchantId={l.merchant_id} />
                          </td>
                        </tr>
                      )}
                    </Fragment>
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

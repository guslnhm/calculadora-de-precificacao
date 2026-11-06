import { useCallback, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { lojaIfoodApi } from "../services/api";
import { cores, estadoInfo, pilula, s, textoMotivos } from "../components/monitor/ui";

const ABAS = [
  { id: "info", rotulo: "Informações" },
  { id: "pausas", rotulo: "Pausas" },
  { id: "horarios", rotulo: "Horário de funcionamento" },
];

const DIAS = [
  ["MONDAY", "Segunda"],
  ["TUESDAY", "Terça"],
  ["WEDNESDAY", "Quarta"],
  ["THURSDAY", "Quinta"],
  ["FRIDAY", "Sexta"],
  ["SATURDAY", "Sábado"],
  ["SUNDAY", "Domingo"],
];

function Api({ children }) {
  return (
    <span style={{ fontFamily: "ui-monospace, Menlo, Consolas, monospace", fontSize: "12px", color: cores.textoSuave }}>
      {children}
    </span>
  );
}

function dataHoraLocal(v) {
  if (!v) return "—";
  const [d, h] = String(v).split("T");
  if (!h) return v;
  const [a, m, dia] = d.split("-");
  return `${dia}/${m}/${a} ${h.slice(0, 5)}`;
}

function Linha({ rotulo, children }) {
  return (
    <div style={{ display: "grid", gridTemplateColumns: "160px 1fr", gap: "10px", padding: "7px 0", borderBottom: `1px solid ${cores.borda}` }}>
      <span style={s.suave}>{rotulo}</span>
      <span style={{ fontSize: "14px", wordBreak: "break-word" }}>{children ?? "—"}</span>
    </div>
  );
}

function Informacoes({ merchantId }) {
  const [info, setInfo] = useState(null);
  const [erro, setErro] = useState("");
  const [bruto, setBruto] = useState(false);
  const [carregando, setCarregando] = useState(false);

  const carregar = useCallback(async () => {
    setCarregando(true);
    try {
      setInfo(await lojaIfoodApi.info(merchantId));
      setErro("");
    } catch (e) {
      setErro(e.message);
    } finally {
      setCarregando(false);
    }
  }, [merchantId]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  if (erro) return <div style={s.erro}>{erro}</div>;
  if (!info) return <div style={s.vazio}>Lendo a loja no iFood…</div>;
  const e = info.endereco || {};
  const endereco = [e.rua && `${e.rua}${e.numero ? `, ${e.numero}` : ""}`, e.complemento, e.bairro, e.cidade && `${e.cidade}${e.estado ? ` - ${e.estado}` : ""}`, e.cep]
    .filter(Boolean)
    .join(" · ");

  return (
    <div style={{ display: "grid", gap: "16px" }}>
      <div style={s.card}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "10px", flexWrap: "wrap", marginBottom: "8px" }}>
          <h3 style={{ margin: 0 }}>Detalhes da loja</h3>
          <span style={{ display: "flex", gap: "8px", alignItems: "center" }}>
            <Api>GET /merchant/v1.0/merchants/{"{id}"}</Api>
            <button style={s.botaoSec} onClick={carregar} disabled={carregando}>
              {carregando ? "Lendo…" : "Atualizar"}
            </button>
          </span>
        </div>
        <Linha rotulo="Nome">{info.nome}</Linha>
        <Linha rotulo="Razão social">{info.razaoSocial}</Linha>
        {info.cnpj && <Linha rotulo="CNPJ">{info.cnpj}</Linha>}
        <Linha rotulo="ID da loja">{info.id}</Linha>
        <Linha rotulo="Endereço">{endereco || null}</Linha>
        {info.tipo && <Linha rotulo="Tipo">{info.tipo}</Linha>}
        {info.ticketMedio != null && (
          <Linha rotulo="Ticket médio">{Number(info.ticketMedio).toLocaleString("pt-BR", { style: "currency", currency: "BRL" })}</Linha>
        )}
        <Linha rotulo="Operações">
          {info.operacoes?.length ? info.operacoes.map((o) => `${o.nome} (${o.canais.join(", ") || "sem canal"})`).join(" · ") : null}
        </Linha>
        <button
          style={{ background: "transparent", border: "none", color: cores.azul, cursor: "pointer", padding: "10px 0 0", fontSize: "13px" }}
          onClick={() => setBruto((b) => !b)}
        >
          {bruto ? "▾ Esconder resposta completa do iFood" : "▸ Ver resposta completa do iFood"}
        </button>
        {bruto && (
          <pre style={{ background: cores.fundo, border: `1px solid ${cores.borda}`, borderRadius: "10px", padding: "12px", overflowX: "auto", fontSize: "12px", maxHeight: "360px" }}>
            {JSON.stringify(info.bruto, null, 2)}
          </pre>
        )}
      </div>

      <div style={s.card}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "10px", flexWrap: "wrap", marginBottom: "10px" }}>
          <h3 style={{ margin: 0 }}>Disponibilidade</h3>
          <Api>GET /merchant/v1.0/merchants/{"{id}"}/status</Api>
        </div>
        {(info.status || []).length === 0 && <div style={s.suave}>O iFood não devolveu status para esta loja.</div>}
        {(info.status || []).map((st, i) => {
          const est = estadoInfo(st.estado);
          return (
            <div key={i} style={{ padding: "8px 0", borderBottom: `1px solid ${cores.borda}` }}>
              <div style={{ display: "flex", gap: "8px", alignItems: "center", flexWrap: "wrap" }}>
                <strong>
                  {st.operacao} · {st.canal}
                </strong>
                <span style={pilula(est.cor)}>{est.rotulo}</span>
                <span style={s.suave}>estado no iFood: {st.estadoIfood}</span>
              </div>
              {st.estado !== "OK" && (
                <div style={{ ...s.suave, marginTop: "4px" }}>
                  {textoMotivos(st.motivos, st.estado) || "—"}
                  {st.mensagem ? ` — ${st.mensagem}` : ""}
                </div>
              )}
            </div>
          );
        })}
        {info.pausas?.length > 0 && (
          <div style={{ ...s.aviso, margin: "12px 0 0" }}>
            {info.pausas.length} pausa(s) ativa(s): veja na aba Pausas.
          </div>
        )}
      </div>
    </div>
  );
}

function Pausas({ merchantId }) {
  const [lista, setLista] = useState(null);
  const [erro, setErro] = useState("");
  const [msg, setMsg] = useState("");
  const [descricao, setDescricao] = useState("");
  const [minutos, setMinutos] = useState("30");
  const [ocupado, setOcupado] = useState("");

  const carregar = useCallback(async () => {
    try {
      setLista(await lojaIfoodApi.pausas(merchantId));
      setErro("");
    } catch (e) {
      setErro(e.message);
    }
  }, [merchantId]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  async function pausar(e) {
    e.preventDefault();
    setOcupado("pausar");
    setMsg("");
    setErro("");
    try {
      const r = await lojaIfoodApi.pausar(merchantId, Number(minutos), descricao);
      setLista(r.pausas);
      setMsg(`Loja pausada no iFood: "${r.criada.descricao}" de ${dataHoraLocal(r.criada.inicio)} até ${dataHoraLocal(r.criada.fim)}.`);
    } catch (err) {
      setErro(err.message);
    } finally {
      setOcupado("");
    }
  }

  async function remover(id) {
    setOcupado(id || "todas");
    setMsg("");
    setErro("");
    try {
      const r = await lojaIfoodApi.removerPausa(merchantId, id);
      setMsg(`${r.removidas.length} pausa(s) removida(s). O iFood pode levar alguns segundos para reabrir a loja.`);
      await carregar();
    } catch (err) {
      setErro(err.message);
    } finally {
      setOcupado("");
    }
  }

  return (
    <div style={{ display: "grid", gap: "16px" }}>
      {erro && <div style={{ ...s.erro, margin: 0 }}>{erro}</div>}
      {msg && <div style={{ ...s.ok, margin: 0 }}>{msg}</div>}
      <form onSubmit={pausar} style={{ ...s.card, display: "grid", gap: "12px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", gap: "10px", flexWrap: "wrap" }}>
          <h3 style={{ margin: 0 }}>Pausar a loja</h3>
          <Api>POST /merchant/v1.0/merchants/{"{id}"}/interruptions</Api>
        </div>
        <div style={{ display: "flex", gap: "12px", flexWrap: "wrap", alignItems: "flex-end" }}>
          <label style={{ ...s.label, flex: "2 1 240px" }}>
            Motivo (aparece no Portal do Parceiro)
            <input style={s.input} value={descricao} onChange={(e) => setDescricao(e.target.value)} placeholder="Ex.: Pausa homologação" />
          </label>
          <label style={{ ...s.label, flex: "1 1 140px" }}>
            Por quanto tempo
            <select style={s.input} value={minutos} onChange={(e) => setMinutos(e.target.value)}>
              {[15, 30, 45, 60, 90, 120, 180, 240].map((m) => (
                <option key={m} value={m}>
                  {m < 60 ? `${m} minutos` : `${m / 60} hora(s)`}
                </option>
              ))}
            </select>
          </label>
          <button type="submit" style={s.botao} disabled={!!ocupado}>
            {ocupado === "pausar" ? "Pausando…" : "Pausar loja"}
          </button>
        </div>
      </form>

      <div style={s.card}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "10px", flexWrap: "wrap", marginBottom: "10px" }}>
          <h3 style={{ margin: 0 }}>Pausas ativas e agendadas</h3>
          <span style={{ display: "flex", gap: "8px", alignItems: "center", flexWrap: "wrap" }}>
            <Api>GET /merchant/v1.0/merchants/{"{id}"}/interruptions</Api>
            <button style={s.botaoSec} onClick={carregar}>
              Atualizar
            </button>
          </span>
        </div>
        {lista === null && !erro && <div style={s.suave}>Carregando…</div>}
        {lista !== null && lista.length === 0 && (
          <div style={s.vazio}>
            Nenhuma pausa ativa.
            <div style={{ ...s.suave, marginTop: "6px" }}>
              Uma pausa recém-criada pode levar alguns segundos para aparecer na lista do iFood: clique em Atualizar.
            </div>
          </div>
        )}
        {(lista || []).map((p) => (
          <div key={p.id} style={{ display: "flex", gap: "10px", alignItems: "center", flexWrap: "wrap", padding: "10px 0", borderBottom: `1px solid ${cores.borda}` }}>
            <span style={pilula(cores.amarelo)}>pausada</span>
            <span style={{ flex: "1 1 200px" }}>
              <strong>{p.descricao || "sem motivo"}</strong>
              <div style={s.suave}>
                {dataHoraLocal(p.inicio)} até {dataHoraLocal(p.fim)}
              </div>
            </span>
            <button style={s.botaoPerigo} onClick={() => remover(p.id)} disabled={!!ocupado}>
              {ocupado === p.id ? "Removendo…" : "Remover pausa"}
            </button>
          </div>
        ))}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "10px", flexWrap: "wrap", marginTop: "12px" }}>
          <Api>DELETE /merchant/v1.0/merchants/{"{id}"}/interruptions/{"{interruptionId}"}</Api>
          <button style={s.botaoSec} onClick={() => remover(null)} disabled={!!ocupado} title="Também acha a pausa pelo status da loja quando a lista ainda não mostrou">
            {ocupado === "todas" ? "Removendo…" : "Remover todas as pausas ativas"}
          </button>
        </div>
      </div>
    </div>
  );
}

function Horarios({ merchantId }) {
  const [dias, setDias] = useState(null);
  const [erro, setErro] = useState("");
  const [msg, setMsg] = useState("");
  const [salvando, setSalvando] = useState(false);

  const carregar = useCallback(async () => {
    try {
      setDias(await lojaIfoodApi.horarios(merchantId));
      setErro("");
    } catch (e) {
      setErro(e.message);
    }
  }, [merchantId]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  function mudar(dia, i, campo, valor) {
    setDias((d) => ({ ...d, [dia]: d[dia].map((f, j) => (j === i ? { ...f, [campo]: valor } : f)) }));
  }

  async function salvar() {
    setSalvando(true);
    setMsg("");
    setErro("");
    try {
      const r = await lojaIfoodApi.definirHorarios(merchantId, dias);
      setDias(r);
      setMsg("Horários salvos no iFood. Abaixo, como o iFood passou a mostrar.");
    } catch (e) {
      setErro(e.message);
    } finally {
      setSalvando(false);
    }
  }

  if (erro && !dias) return <div style={s.erro}>{erro}</div>;
  if (!dias) return <div style={s.vazio}>Lendo os horários no iFood…</div>;

  return (
    <div style={s.card}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "10px", flexWrap: "wrap", marginBottom: "6px" }}>
        <h3 style={{ margin: 0 }}>Horário de funcionamento</h3>
        <span style={{ display: "flex", gap: "8px", alignItems: "center", flexWrap: "wrap" }}>
          <Api>GET / PUT /merchant/v1.0/merchants/{"{id}"}/opening-hours</Api>
          <button style={s.botaoSec} onClick={carregar}>
            Recarregar
          </button>
        </span>
      </div>
      <p style={{ ...s.suave, marginTop: 0 }}>
        O iFood substitui todos os horários de uma vez: dia sem faixa fica fechado. Fim às 00:00 vale como 23:59.
      </p>
      {erro && <div style={s.erro}>{erro}</div>}
      {msg && <div style={s.ok}>{msg}</div>}
      <div style={{ display: "grid", gap: "4px" }}>
        {DIAS.map(([dia, nome]) => (
          <div key={dia} style={{ display: "flex", gap: "12px", alignItems: "center", flexWrap: "wrap", padding: "10px 0", borderBottom: `1px solid ${cores.borda}` }}>
            <strong style={{ width: "80px" }}>{nome}</strong>
            <div style={{ display: "flex", gap: "8px", flexWrap: "wrap", alignItems: "center", flex: 1 }}>
              {(dias[dia] || []).length === 0 && <span style={pilula(cores.cinza)}>fechado</span>}
              {(dias[dia] || []).map((f, i) => (
                <span key={i} style={{ display: "inline-flex", gap: "4px", alignItems: "center", border: `1px solid ${cores.bordaCampo}`, borderRadius: "10px", padding: "4px 6px" }}>
                  <input type="time" value={f.inicio} onChange={(e) => mudar(dia, i, "inicio", e.target.value)} style={{ ...s.input, width: "auto", padding: "4px 6px" }} aria-label={`${nome} início`} />
                  <span style={s.suave}>às</span>
                  <input type="time" value={f.fim} onChange={(e) => mudar(dia, i, "fim", e.target.value)} style={{ ...s.input, width: "auto", padding: "4px 6px" }} aria-label={`${nome} fim`} />
                  <button
                    style={{ background: "transparent", border: "none", color: cores.vermelho, cursor: "pointer", fontSize: "16px" }}
                    onClick={() => setDias((d) => ({ ...d, [dia]: d[dia].filter((_, j) => j !== i) }))}
                    aria-label={`Remover faixa de ${nome}`}
                  >
                    ×
                  </button>
                </span>
              ))}
              <button
                style={{ ...s.botaoSec, padding: "5px 10px" }}
                onClick={() => setDias((d) => ({ ...d, [dia]: [...(d[dia] || []), { inicio: "10:00", fim: "22:00" }] }))}
              >
                + faixa
              </button>
            </div>
          </div>
        ))}
      </div>
      <div style={{ display: "flex", justifyContent: "flex-end", marginTop: "14px" }}>
        <button style={s.botao} onClick={salvar} disabled={salvando}>
          {salvando ? "Salvando no iFood…" : "Salvar horários no iFood"}
        </button>
      </div>
    </div>
  );
}

export default function LojaIfoodPage() {
  const [params, setParams] = useSearchParams();
  const merchantId = params.get("loja") || "";
  const aba = ABAS.some((a) => a.id === params.get("aba")) ? params.get("aba") : "info";
  const [lojas, setLojas] = useState(null);
  const [erro, setErro] = useState("");

  useEffect(() => {
    lojaIfoodApi
      .lojas()
      .then((l) => {
        setLojas(l);
        if (!merchantId && l.length === 1) setParams({ loja: l[0].id });
      })
      .catch((e) => setErro(e.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div style={s.page}>
      <div style={s.header}>
        <div>
          <h2 style={s.titulo}>Loja iFood</h2>
          <p style={s.subtitulo}>Informações, disponibilidade, pausas e horário de funcionamento das lojas no iFood.</p>
        </div>
      </div>
      {erro && <div style={s.erro}>{erro}</div>}

      <div style={s.card}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "10px", flexWrap: "wrap", marginBottom: "10px" }}>
          <h3 style={{ margin: 0 }}>Lojas vinculadas ao aplicativo ({lojas ? lojas.length : "…"})</h3>
          <Api>GET /merchant/v1.0/merchants</Api>
        </div>
        {lojas === null && !erro && <div style={s.suave}>Carregando…</div>}
        {lojas !== null && lojas.length === 0 && <div style={s.suave}>Nenhuma loja autorizou o aplicativo ainda.</div>}
        <div style={{ display: "grid", gap: "6px" }}>
          {(lojas || []).map((l) => (
            <button
              key={l.id}
              onClick={() => setParams({ loja: l.id, aba })}
              style={{
                textAlign: "left",
                background: l.id === merchantId ? "#1F2A37" : "transparent",
                border: `1px solid ${l.id === merchantId ? cores.destaque : cores.borda}`,
                borderRadius: "10px",
                color: cores.texto,
                padding: "10px 14px",
                cursor: "pointer",
              }}
            >
              <strong>{l.nome}</strong>
              <div style={s.suave}>
                {l.razaoSocial || ""} · {l.id}
              </div>
            </button>
          ))}
        </div>
      </div>

      {merchantId && (
        <div style={{ marginTop: "18px" }}>
          <div style={s.abas}>
            {ABAS.map((a) => (
              <button key={a.id} style={{ ...s.aba, ...(aba === a.id ? s.abaAtiva : {}) }} onClick={() => setParams({ loja: merchantId, aba: a.id })}>
                {a.rotulo}
              </button>
            ))}
          </div>
          {aba === "info" && <Informacoes key={merchantId} merchantId={merchantId} />}
          {aba === "pausas" && <Pausas key={merchantId} merchantId={merchantId} />}
          {aba === "horarios" && <Horarios key={merchantId} merchantId={merchantId} />}
        </div>
      )}
    </div>
  );
}

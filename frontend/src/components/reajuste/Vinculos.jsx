import { useCallback, useEffect, useMemo, useState } from "react";
import { reajusteApi } from "../../services/api";
import { cores, pilula, s } from "../monitor/ui";
import { reais } from "../cardapio/Controles";

const FILTROS = [
  { id: "PENDENTES", rotulo: "Para revisar" },
  { id: "VINCULADOS", rotulo: "Ligados" },
  { id: "IGNORADOS", rotulo: "Ignorados" },
  { id: "TODOS", rotulo: "Todos" },
];

function situacao(e) {
  if (e.vinculo?.ignorado) return "IGNORADO";
  if (e.vinculo?.itemId) return "VINCULADO";
  if (e.sugestao) return "SUGESTAO";
  return "SEM";
}

function normalizar(t) {
  return String(t || "")
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase();
}

function Linha({ e, itens, ocupado, onSalvar }) {
  const sit = situacao(e);
  const valor = e.vinculo?.itemId ?? (sit === "SUGESTAO" ? e.sugestao.itemId : "");
  const item = itens.find((i) => i.id === Number(valor));
  return (
    <div
      style={{
        display: "grid",
        gridTemplateColumns: "minmax(180px, 1.1fr) minmax(220px, 1.4fr) auto",
        gap: "12px",
        alignItems: "center",
        padding: "10px 0",
        borderBottom: `1px solid ${cores.borda}`,
        opacity: sit === "IGNORADO" ? 0.55 : 1,
      }}
      className="linha-vinculo"
    >
      <div style={{ minWidth: 0 }}>
        <div style={{ fontWeight: "bold" }}>{e.nome}</div>
        <div style={s.suave}>
          {e.tipo === "COMPLEMENTO" ? `complemento · ${e.categoria}` : e.categoria} · {reais(e.preco)}
        </div>
      </div>
      <div style={{ display: "grid", gap: "4px", minWidth: 0 }}>
        <select
          style={{
            ...s.input,
            borderColor: sit === "SUGESTAO" ? cores.amarelo : sit === "VINCULADO" ? cores.verde : cores.bordaCampo,
          }}
          value={sit === "IGNORADO" ? "" : valor}
          disabled={ocupado}
          onChange={(ev) => onSalvar(e, ev.target.value ? Number(ev.target.value) : null, false)}
          aria-label={`Item da calculadora para ${e.nome}`}
        >
          <option value="">— sem vínculo —</option>
          {itens.map((i) => (
            <option key={i.id} value={i.id}>
              {i.nome} · CMV {reais(i.cmv)}
            </option>
          ))}
        </select>
        <div style={{ display: "flex", gap: "8px", alignItems: "center", flexWrap: "wrap", fontSize: "12px" }}>
          {sit === "SUGESTAO" && (
            <span style={pilula(cores.amarelo)}>sugestão · {Math.round(e.sugestao.similaridade * 100)}% parecido</span>
          )}
          {sit === "VINCULADO" && (
            <span style={pilula(cores.verde)}>
              ligado{e.vinculo.origem === "AUTO" ? " (sugestão aceita)" : ""}
            </span>
          )}
          {sit === "IGNORADO" && <span style={pilula(cores.cinza)}>não reajustar</span>}
          {item?.precoCalculado != null && sit !== "IGNORADO" && (
            <span style={s.suave}>
              calculadora: {reais(item.precoCalculado)}
              {e.preco != null && Number(item.precoCalculado) !== Number(e.preco) && (
                <span style={{ color: Number(item.precoCalculado) > Number(e.preco) ? cores.amarelo : cores.azul }}>
                  {" "}
                  ({Number(item.precoCalculado) > Number(e.preco) ? "+" : ""}
                  {(((item.precoCalculado - e.preco) / e.preco) * 100).toFixed(1).replace(".", ",")}%)
                </span>
              )}
            </span>
          )}
        </div>
      </div>
      <div style={{ display: "flex", gap: "6px", justifyContent: "flex-end", flexWrap: "wrap" }}>
        {sit === "SUGESTAO" && (
          <button style={{ ...s.botao, padding: "6px 10px" }} disabled={ocupado} onClick={() => onSalvar(e, e.sugestao.itemId, false)}>
            Aceitar
          </button>
        )}
        {sit !== "IGNORADO" ? (
          <button style={{ ...s.botaoSec, padding: "5px 10px" }} disabled={ocupado} onClick={() => onSalvar(e, null, true)}>
            Ignorar
          </button>
        ) : (
          <button style={{ ...s.botaoSec, padding: "5px 10px" }} disabled={ocupado} onClick={() => onSalvar(e, null, false)}>
            Voltar a considerar
          </button>
        )}
      </div>
    </div>
  );
}

export default function Vinculos({ lojaId, lojasIfood }) {
  const [merchantId, setMerchantId] = useState(lojasIfood[0]?.merchantId || "");
  const [dados, setDados] = useState(null);
  const [erro, setErro] = useState("");
  const [msg, setMsg] = useState("");
  const [filtro, setFiltro] = useState("PENDENTES");
  const [tipo, setTipo] = useState("ITEM");
  const [busca, setBusca] = useState("");
  const [ocupado, setOcupado] = useState(null);

  const carregar = useCallback(async () => {
    if (!merchantId) return;
    try {
      setDados(await reajusteApi.vinculos(lojaId, merchantId));
      setErro("");
    } catch (e) {
      setErro(e.message);
    }
  }, [lojaId, merchantId]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  async function salvar(e, itemId, ignorado) {
    setOcupado(`${e.tipo}|${e.ifoodId}`);
    setMsg("");
    try {
      const r = await reajusteApi.salvarVinculos(lojaId, merchantId, [
        { tipo: e.tipo, ifoodId: e.ifoodId, ifoodNome: e.nome, itemId, ignorado },
      ]);
      setDados(r);
    } catch (err) {
      setErro(err.message);
    } finally {
      setOcupado(null);
    }
  }

  async function aceitarTodas(minimo) {
    setOcupado("todas");
    try {
      const r = await reajusteApi.aceitarSugestoes(lojaId, merchantId, minimo);
      setMsg(`${r.aceitas} sugestão(ões) aceita(s).`);
      await carregar();
    } catch (err) {
      setErro(err.message);
    } finally {
      setOcupado(null);
    }
  }

  const contagem = useMemo(() => {
    const c = { VINCULADO: 0, SUGESTAO: 0, SEM: 0, IGNORADO: 0, fortes: 0 };
    (dados?.entidades || [])
      .filter((e) => e.tipo === tipo)
      .forEach((e) => {
        const st = situacao(e);
        c[st]++;
        if (st === "SUGESTAO" && e.sugestao.similaridade >= 0.9) c.fortes++;
      });
    return c;
  }, [dados, tipo]);

  const visiveis = useMemo(() => {
    const t = normalizar(busca.trim());
    return (dados?.entidades || []).filter((e) => {
      if (e.tipo !== tipo) return false;
      const st = situacao(e);
      if (filtro === "PENDENTES" && !(st === "SUGESTAO" || st === "SEM")) return false;
      if (filtro === "VINCULADOS" && st !== "VINCULADO") return false;
      if (filtro === "IGNORADOS" && st !== "IGNORADO") return false;
      return !t || normalizar(e.nome).includes(t) || normalizar(e.categoria).includes(t);
    });
  }, [dados, filtro, tipo, busca]);

  if (!lojasIfood.length) {
    return <div style={s.aviso}>Ligue pelo menos uma loja do iFood na aba “Lojas e regras”.</div>;
  }

  return (
    <div>
      <style>{`@media (max-width: 720px) { .linha-vinculo { grid-template-columns: 1fr !important; } }`}</style>
      {lojasIfood.length > 1 && (
        <div style={s.abas}>
          {lojasIfood.map((l) => (
            <button
              key={l.merchantId}
              style={{ ...s.aba, ...(l.merchantId === merchantId ? s.abaAtiva : {}) }}
              onClick={() => {
                setDados(null);
                setMerchantId(l.merchantId);
              }}
            >
              {l.nome}
            </button>
          ))}
        </div>
      )}
      <p style={{ ...s.suave, marginTop: 0 }}>
        Diga qual item da calculadora dá o preço de cada item do iFood. As sugestões vêm pela semelhança dos nomes;
        só entram no reajuste os itens <strong>ligados</strong>.
      </p>
      {erro && <div style={s.erro}>{erro}</div>}
      {msg && <div style={s.ok}>{msg}</div>}
      {!dados && !erro && <div style={s.vazio}>Carregando o cardápio e os itens da calculadora…</div>}

      {dados && (
        <>
          <div style={{ display: "flex", gap: "10px", flexWrap: "wrap", alignItems: "center", marginBottom: "12px" }}>
            <span style={pilula(cores.verde)}>{contagem.VINCULADO} ligados</span>
            <span style={pilula(cores.amarelo)}>{contagem.SUGESTAO} sugestões</span>
            <span style={pilula(cores.vermelho)}>{contagem.SEM} sem vínculo</span>
            <span style={pilula(cores.cinza)}>{contagem.IGNORADO} ignorados</span>
            <span style={{ marginLeft: "auto", display: "flex", gap: "8px", flexWrap: "wrap" }}>
              <button style={s.botao} disabled={!contagem.fortes || ocupado === "todas"} onClick={() => aceitarTodas(0.9)}>
                Aceitar {contagem.fortes} sugestão(ões) ≥ 90%
              </button>
              <button
                style={s.botaoSec}
                disabled={!contagem.SUGESTAO || ocupado === "todas"}
                onClick={() => aceitarTodas(0)}
              >
                Aceitar todas
              </button>
            </span>
          </div>

          <div style={{ display: "flex", gap: "8px", flexWrap: "wrap", alignItems: "center", marginBottom: "12px" }}>
            <select style={{ ...s.input, width: "auto" }} value={tipo} onChange={(e) => setTipo(e.target.value)}>
              <option value="ITEM">Itens</option>
              <option value="COMPLEMENTO">Complementos</option>
            </select>
            {FILTROS.map((f) => (
              <button
                key={f.id}
                style={{ ...s.botaoSec, ...(filtro === f.id ? { borderColor: cores.destaque, color: cores.texto } : {}) }}
                onClick={() => setFiltro(f.id)}
              >
                {f.rotulo}
              </button>
            ))}
            <input
              style={{ ...s.input, maxWidth: "260px" }}
              placeholder="Buscar…"
              value={busca}
              onChange={(e) => setBusca(e.target.value)}
            />
          </div>

          <div style={{ ...s.card, padding: "6px 18px" }}>
            {visiveis.length === 0 && (
              <div style={s.vazio}>
                {filtro === "PENDENTES" ? "Nada para revisar aqui. ✅" : "Nada neste filtro."}
              </div>
            )}
            {visiveis.map((e) => (
              <Linha
                key={`${e.tipo}|${e.ifoodId}`}
                e={e}
                itens={dados.itensCalculadora}
                ocupado={ocupado === `${e.tipo}|${e.ifoodId}` || ocupado === "todas"}
                onSalvar={salvar}
              />
            ))}
          </div>
          {dados.itensCalculadora.length === 0 && (
            <div style={{ ...s.aviso, marginTop: "12px" }}>
              Esta loja não tem itens ativos na calculadora. Importe a planilha de custos na página Itens.
            </div>
          )}
        </>
      )}
    </div>
  );
}

import { useCallback, useEffect, useMemo, useState } from "react";
import { baseApi } from "../../services/api";
import { botao, campo, cor } from "./fmt";

// Lojas com pedidos na base que ainda não têm operação (os pedidos não entram na fato nem na gold).
// Aqui dá para ligar cada uma a uma operação existente ou criar a operação.

function Cadastro({ loja, operacoes, onFeito, onCancelar }) {
  const [modo, setModo] = useState("existente");
  const [idOperacao, setIdOperacao] = useState("");
  const [idLoja, setIdLoja] = useState("");
  const [nomeLoja, setNomeLoja] = useState("");
  const [nomeOperacao, setNomeOperacao] = useState("");
  const [tipoEntrega, setTipoEntrega] = useState("Parceira");
  const [enviando, setEnviando] = useState(false);
  const [erro, setErro] = useState("");

  const marcas = useMemo(() => {
    const m = new Map();
    operacoes.forEach((o) => m.set(o.idLoja, o.loja));
    return [...m.entries()].sort((a, b) => String(a[1]).localeCompare(String(b[1])));
  }, [operacoes]);

  async function salvar() {
    setEnviando(true);
    setErro("");
    try {
      const dados = { idLojaOrigem: loja.idLoja, nomeOrigem: loja.nome };
      if (modo === "existente") {
        dados.idOperacao = Number(idOperacao);
      } else {
        dados.nomeOperacao = nomeOperacao;
        dados.tipoEntrega = tipoEntrega;
        if (idLoja === "nova") dados.nomeLoja = nomeLoja;
        else dados.idLoja = Number(idLoja);
      }
      const r = await baseApi.cadastrarOperacao(dados);
      onFeito(`${loja.nome}: operação cadastrada, ${r.pedidos} pedido(s) entraram na gold.`);
    } catch (e) {
      setErro(e.message);
    } finally {
      setEnviando(false);
    }
  }

  const pronto = modo === "existente" ? !!idOperacao : !!nomeOperacao.trim() && (idLoja === "nova" ? !!nomeLoja.trim() : !!idLoja);

  return (
    <div style={{ display: "grid", gap: 8, padding: "10px 12px", background: cor.campo, borderRadius: 8, marginTop: 6 }}>
      <div style={{ display: "flex", gap: 14, fontSize: 13, flexWrap: "wrap" }}>
        <label><input type="radio" checked={modo === "existente"} onChange={() => setModo("existente")} /> Ligar a uma operação que já existe</label>
        <label><input type="radio" checked={modo === "nova"} onChange={() => setModo("nova")} /> Criar operação nova</label>
      </div>
      {modo === "existente" ? (
        <select value={idOperacao} onChange={(e) => setIdOperacao(e.target.value)} style={{ ...campo, maxWidth: 420 }}>
          <option value="">Escolha a operação…</option>
          {operacoes.map((o) => <option key={o.idOperacao} value={o.idOperacao}>{o.loja} · {o.operacao}</option>)}
        </select>
      ) : (
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          <select value={idLoja} onChange={(e) => setIdLoja(e.target.value)} style={{ ...campo, minWidth: 200 }}>
            <option value="">Marca (loja)…</option>
            {marcas.map(([id, nome]) => <option key={id} value={id}>{nome}</option>)}
            <option value="nova">+ Marca nova</option>
          </select>
          {idLoja === "nova" && (
            <input placeholder="Nome da marca" value={nomeLoja} onChange={(e) => setNomeLoja(e.target.value)} style={campo} />
          )}
          <input placeholder="Operação (ex.: Asa Norte)" value={nomeOperacao} onChange={(e) => setNomeOperacao(e.target.value)} style={campo} />
          <select value={tipoEntrega} onChange={(e) => setTipoEntrega(e.target.value)} style={campo}>
            <option value="Parceira">Entrega parceira</option>
            <option value="Própria">Entrega própria</option>
          </select>
        </div>
      )}
      {erro && <div style={{ fontSize: 13, color: cor.ruim }}>{erro}</div>}
      <div style={{ display: "flex", gap: 8 }}>
        <button style={botao} onClick={salvar} disabled={!pronto || enviando}>{enviando ? "Gravando…" : "Cadastrar e passar os pedidos"}</button>
        <button style={{ ...botao, background: "transparent", color: cor.texto, border: `1px solid ${cor.bordaCampo}` }} onClick={onCancelar}>Cancelar</button>
      </div>
    </div>
  );
}

export default function LojasSemOperacao({ atualizar }) {
  const [lojas, setLojas] = useState(null);
  const [operacoes, setOperacoes] = useState([]);
  const [aberta, setAberta] = useState(null);
  const [aviso, setAviso] = useState("");
  const [erro, setErro] = useState("");

  const carregar = useCallback(() => {
    Promise.all([baseApi.semOperacao(), baseApi.operacoes()])
      .then(([l, o]) => {
        setLojas(l);
        setOperacoes(o);
        setErro("");
      })
      .catch((e) => setErro(e.message));
  }, []);

  useEffect(() => {
    carregar();
  }, [carregar, atualizar]);

  if (erro) return <div style={{ fontSize: 13, color: cor.ruim }}>Lojas sem operação: {erro}</div>;
  if (!lojas) return null;
  if (lojas.length === 0) {
    return aviso ? <div style={{ fontSize: 13, color: cor.bom }}>{aviso}</div> : null;
  }
  return (
    <div style={{ fontSize: 13 }}>
      <div style={{ color: cor.ruim, fontWeight: 600 }}>
        {lojas.length} loja(s) sem operação na base: os pedidos estão na tabela pedidos, mas fora da silver e da gold.
      </div>
      {aviso && <div style={{ color: cor.bom, marginTop: 4 }}>{aviso}</div>}
      {lojas.map((l) => (
        <div key={l.idLoja} style={{ padding: "6px 0", borderBottom: `1px solid ${cor.grade}` }}>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <span>{l.nome} <span style={{ color: cor.textoSuave }}>(ID {l.idLoja} · {l.pedidos.toLocaleString("pt-BR")} pedido(s))</span></span>
            {aberta !== l.idLoja && (
              <button style={{ ...botao, padding: "4px 10px", fontSize: 12, marginLeft: "auto" }} onClick={() => setAberta(l.idLoja)}>Cadastrar operação</button>
            )}
          </div>
          {aberta === l.idLoja && (
            <Cadastro loja={l} operacoes={operacoes} onCancelar={() => setAberta(null)}
                      onFeito={(msg) => { setAberta(null); setAviso(msg); carregar(); }} />
          )}
        </div>
      ))}
    </div>
  );
}

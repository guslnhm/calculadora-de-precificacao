import { useEffect, useMemo, useState } from "react";
import { monitorApi, whatsappApi } from "../../services/api";
import { cores, pilula, s } from "./ui";

const VAZIO = {
  id: null,
  nome: "",
  chatId: "",
  admin: false,
  todasLojas: false,
  avisarRetorno: true,
  ativo: true,
  lojas: [],
};

function normalizar(texto) {
  return String(texto || "")
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase();
}

/** Escolhe o grupo na lista da Z-API ou aceita o ID colado à mão. */
function EscolherGrupo({ chatId, onEscolher }) {
  const [disponiveis, setDisponiveis] = useState(null);
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState("");
  const [busca, setBusca] = useState("");

  async function buscar() {
    setCarregando(true);
    setErro("");
    try {
      const lista = await whatsappApi.gruposDisponiveis();
      setDisponiveis(lista || []);
    } catch (e) {
      setErro(e.message);
    } finally {
      setCarregando(false);
    }
  }

  const filtrados = useMemo(() => {
    const t = normalizar(busca);
    return (disponiveis || []).filter((g) => !t || normalizar(g.nome).includes(t));
  }, [disponiveis, busca]);

  return (
    <div style={{ display: "grid", gap: "8px" }}>
      <div style={s.label}>Grupo no WhatsApp</div>
      {disponiveis === null ? (
        <button type="button" style={s.botaoSec} onClick={buscar} disabled={carregando}>
          {carregando ? "Buscando grupos…" : "Buscar os grupos do número conectado"}
        </button>
      ) : (
        <>
          <input
            style={s.input}
            placeholder={`Filtrar ${disponiveis.length} grupo(s)…`}
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
          />
          <div
            style={{
              maxHeight: "180px",
              overflowY: "auto",
              border: `1px solid ${cores.bordaCampo}`,
              borderRadius: "10px",
            }}
          >
            {filtrados.length === 0 && <div style={{ ...s.suave, padding: "10px" }}>Nenhum grupo encontrado.</div>}
            {filtrados.map((g) => (
              <button
                type="button"
                key={g.id}
                onClick={() => onEscolher(g)}
                style={{
                  display: "block",
                  width: "100%",
                  textAlign: "left",
                  background: g.id === chatId ? "#1F2A37" : "transparent",
                  color: cores.texto,
                  border: "none",
                  borderBottom: `1px solid ${cores.borda}`,
                  padding: "9px 12px",
                  cursor: "pointer",
                }}
              >
                {g.id === chatId ? "✓ " : ""}
                {g.nome || "(sem nome)"}
              </button>
            ))}
          </div>
        </>
      )}
      {erro && <div style={{ ...s.erro, margin: 0 }}>Não consegui listar os grupos: {erro}</div>}
      <input
        style={s.input}
        placeholder="ID do grupo (preenchido ao escolher acima)"
        value={chatId}
        onChange={(e) => onEscolher({ id: e.target.value })}
      />
    </div>
  );
}

function FormGrupo({ inicial, lojas, onSalvo, onCancelar }) {
  const [g, setG] = useState(inicial);
  const [busca, setBusca] = useState("");
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState("");

  const selecionadas = new Set(g.lojas);
  const filtradas = useMemo(() => {
    const t = normalizar(busca);
    return lojas.filter((l) => !t || normalizar(l.nome).includes(t) || normalizar(l.razaoSocial).includes(t));
  }, [lojas, busca]);

  function mudar(campo, valor) {
    setG((atual) => ({ ...atual, [campo]: valor }));
  }

  function alternarLoja(id) {
    setG((atual) => {
      const set = new Set(atual.lojas);
      if (set.has(id)) set.delete(id);
      else set.add(id);
      return { ...atual, lojas: [...set] };
    });
  }

  function marcarFiltradas(marcar) {
    setG((atual) => {
      const set = new Set(atual.lojas);
      filtradas.forEach((l) => (marcar ? set.add(l.merchantId) : set.delete(l.merchantId)));
      return { ...atual, lojas: [...set] };
    });
  }

  async function salvar(e) {
    e.preventDefault();
    setSalvando(true);
    setErro("");
    try {
      const dados = {
        nome: g.nome,
        chatId: g.chatId,
        admin: g.admin,
        todasLojas: g.todasLojas,
        avisarRetorno: g.avisarRetorno,
        ativo: g.ativo,
        lojas: g.todasLojas ? [] : g.lojas,
      };
      const salvo = g.id ? await whatsappApi.atualizarGrupo(g.id, dados) : await whatsappApi.criarGrupo(dados);
      onSalvo(salvo);
    } catch (e2) {
      setErro(e2.message);
    } finally {
      setSalvando(false);
    }
  }

  return (
    <form onSubmit={salvar} style={{ ...s.card, display: "grid", gap: "14px" }}>
      <h3 style={{ ...s.cardTitulo, margin: 0 }}>{g.id ? `Editar "${inicial.nome}"` : "Novo grupo"}</h3>

      <EscolherGrupo
        chatId={g.chatId}
        onEscolher={(escolhido) =>
          setG((atual) => ({
            ...atual,
            chatId: escolhido.id ?? "",
            nome: atual.nome || escolhido.nome || "",
          }))
        }
      />

      <label style={s.label}>
        Nome (como vai aparecer aqui)
        <input style={s.input} value={g.nome} onChange={(e) => mudar("nome", e.target.value)} />
      </label>

      <div style={{ display: "grid", gap: "8px" }}>
        <label style={s.check}>
          <input type="checkbox" checked={g.admin} onChange={(e) => mudar("admin", e.target.checked)} />
          <span>
            Grupo admin <span style={s.suave}>(avisos do monitor e alertas das lojas sem grupo)</span>
          </span>
        </label>
        <label style={s.check}>
          <input type="checkbox" checked={g.todasLojas} onChange={(e) => mudar("todasLojas", e.target.checked)} />
          <span>Recebe alertas de todas as lojas</span>
        </label>
        <label style={s.check}>
          <input
            type="checkbox"
            checked={g.avisarRetorno}
            onChange={(e) => mudar("avisarRetorno", e.target.checked)}
          />
          <span>Avisar quando a loja voltar ✅</span>
        </label>
        <label style={s.check}>
          <input type="checkbox" checked={g.ativo} onChange={(e) => mudar("ativo", e.target.checked)} />
          <span>Ativo</span>
        </label>
      </div>

      {!g.todasLojas && (
        <div style={{ display: "grid", gap: "8px" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "8px" }}>
            <span style={s.label}>
              Lojas deste grupo ({g.lojas.length} de {lojas.length})
            </span>
            <span style={{ display: "flex", gap: "6px" }}>
              <button type="button" style={{ ...s.botaoSec, padding: "5px 10px" }} onClick={() => marcarFiltradas(true)}>
                Marcar {busca ? "filtradas" : "todas"}
              </button>
              <button type="button" style={{ ...s.botaoSec, padding: "5px 10px" }} onClick={() => marcarFiltradas(false)}>
                Desmarcar
              </button>
            </span>
          </div>
          <input
            style={s.input}
            placeholder="Buscar loja…"
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
          />
          <div
            style={{
              maxHeight: "260px",
              overflowY: "auto",
              border: `1px solid ${cores.bordaCampo}`,
              borderRadius: "10px",
              padding: "6px 10px",
            }}
          >
            {lojas.length === 0 && (
              <div style={{ ...s.suave, padding: "6px 0" }}>
                Nenhuma loja do iFood ainda. Elas aparecem quando autorizam o aplicativo.
              </div>
            )}
            {filtradas.map((l) => (
              <label key={l.merchantId} style={{ ...s.check, padding: "6px 0" }}>
                <input
                  type="checkbox"
                  checked={selecionadas.has(l.merchantId)}
                  onChange={() => alternarLoja(l.merchantId)}
                />
                <span>{l.nome}</span>
              </label>
            ))}
          </div>
        </div>
      )}

      {erro && <div style={{ ...s.erro, margin: 0 }}>{erro}</div>}

      <div style={{ display: "flex", gap: "8px", justifyContent: "flex-end" }}>
        <button type="button" style={s.botaoSec} onClick={onCancelar}>
          Cancelar
        </button>
        <button type="submit" style={s.botao} disabled={salvando || !g.chatId || !g.nome.trim()}>
          {salvando ? "Salvando…" : "Salvar grupo"}
        </button>
      </div>
    </form>
  );
}

function CartaoGrupo({ g, nomes, onEditar, onRemovido }) {
  const [teste, setTeste] = useState("");
  const [testando, setTestando] = useState(false);
  const [confirmar, setConfirmar] = useState(false);

  async function testar() {
    setTestando(true);
    setTeste("");
    try {
      await whatsappApi.testarGrupo(g.id);
      setTeste("ok");
    } catch (e) {
      setTeste(e.message);
    } finally {
      setTestando(false);
    }
  }

  async function remover() {
    try {
      await whatsappApi.removerGrupo(g.id);
      onRemovido();
    } catch (e) {
      setTeste(e.message);
    }
  }

  const lojas = (g.lojas || []).map((id) => nomes[id] || id);

  return (
    <div style={{ ...s.card, padding: "16px", opacity: g.ativo ? 1 : 0.6 }}>
      <div style={{ display: "flex", gap: "8px", alignItems: "center", flexWrap: "wrap" }}>
        <strong style={{ fontSize: "16px" }}>{g.nome}</strong>
        {g.admin && <span style={pilula(cores.destaque)}>Admin</span>}
        {!g.ativo && <span style={pilula(cores.cinza)}>Inativo</span>}
        {!g.avisarRetorno && <span style={pilula(cores.cinza)}>Sem aviso de retorno</span>}
      </div>
      <div style={{ ...s.suave, margin: "6px 0 10px" }}>
        {g.todasLojas
          ? "Todas as lojas"
          : lojas.length
            ? `${lojas.length} loja(s): ${lojas.slice(0, 6).join(", ")}${lojas.length > 6 ? "…" : ""}`
            : g.admin
              ? "Só avisos do monitor e lojas sem grupo"
              : "Nenhuma loja marcada"}
      </div>
      <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
        <button style={s.botaoSec} onClick={onEditar}>
          Editar
        </button>
        <button
          style={s.botaoSec}
          onClick={testar}
          disabled={testando}
          title="Envia de verdade, mesmo no modo sombra"
        >
          {testando ? "Enviando…" : "Enviar teste"}
        </button>
        {confirmar ? (
          <>
            <button style={s.botaoPerigo} onClick={remover}>
              Confirmar remoção
            </button>
            <button style={s.botaoSec} onClick={() => setConfirmar(false)}>
              Não
            </button>
          </>
        ) : (
          <button style={s.botaoPerigo} onClick={() => setConfirmar(true)}>
            Remover
          </button>
        )}
      </div>
      {teste === "ok" && <div style={{ ...s.ok, margin: "10px 0 0" }}>Mensagem de teste enviada. Confira no grupo.</div>}
      {teste && teste !== "ok" && <div style={{ ...s.erro, margin: "10px 0 0" }}>{teste}</div>}
    </div>
  );
}

export default function AbaGrupos() {
  const [grupos, setGrupos] = useState(null);
  const [lojas, setLojas] = useState([]);
  const [erro, setErro] = useState("");
  const [editando, setEditando] = useState(null);
  const [situacao, setSituacao] = useState(null);

  async function carregar() {
    try {
      const [gs, ls] = await Promise.all([whatsappApi.grupos(), monitorApi.lojasIfood()]);
      setGrupos(gs);
      setLojas([...ls].sort((a, b) => String(a.nome).localeCompare(String(b.nome), "pt-BR")));
      setErro("");
    } catch (e) {
      setErro(e.message);
    }
  }

  useEffect(() => {
    carregar();
    whatsappApi
      .situacao()
      .then(setSituacao)
      .catch((e) => setSituacao({ erro: e.message }));
  }, []);

  const nomes = useMemo(() => Object.fromEntries(lojas.map((l) => [l.merchantId, l.nome])), [lojas]);

  const ativos = (grupos || []).filter((g) => g.ativo);
  const temAdmin = ativos.some((g) => g.admin);
  const semGrupo = useMemo(() => {
    if (ativos.some((g) => g.todasLojas && !g.admin)) return [];
    const mapeadas = new Set(ativos.filter((g) => !g.admin).flatMap((g) => g.lojas || []));
    return lojas.filter((l) => !mapeadas.has(l.merchantId));
  }, [ativos, lojas]);

  const zapiConectada = situacao && !situacao.erro && (situacao.connected === true || situacao.conectado === true);
  const soConsole = situacao && situacao.provider === "console";

  return (
    <div>
      {situacao?.erro && <div style={s.erro}>Não consegui falar com a Z-API: {situacao.erro}</div>}
      {soConsole && (
        <div style={s.aviso}>
          A Z-API não está configurada no servidor: as mensagens só vão para o log. Confira as variáveis ZAPI_* no
          Railway.
        </div>
      )}
      {situacao && !situacao.erro && !soConsole && !zapiConectada && (
        <div style={s.aviso}>
          O número da Z-API parece desconectado. Abra o painel da Z-API e leia o QR Code de novo.
        </div>
      )}
      {grupos !== null && !temAdmin && (
        <div style={s.aviso}>
          Cadastre um <strong>grupo admin</strong> primeiro: é para ele que vão os avisos do monitor e os alertas das
          lojas que ainda não estão em nenhum grupo.
        </div>
      )}
      {erro && <div style={s.erro}>{erro}</div>}

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))",
          gap: "16px",
          alignItems: "start",
        }}
      >
        <div style={{ display: "grid", gap: "12px" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <h3 style={{ ...s.cardTitulo, margin: 0 }}>Grupos ({(grupos || []).length})</h3>
            <button style={s.botao} onClick={() => setEditando({ ...VAZIO, admin: !temAdmin })}>
              + Novo grupo
            </button>
          </div>

          {grupos === null && !erro && <div style={s.vazio}>Carregando…</div>}
          {grupos !== null && grupos.length === 0 && (
            <div style={{ ...s.card, ...s.vazio }}>Nenhum grupo cadastrado ainda.</div>
          )}
          {(grupos || []).map((g) => (
            <CartaoGrupo
              key={g.id}
              g={g}
              nomes={nomes}
              onEditar={() => setEditando({ ...g, lojas: [...(g.lojas || [])] })}
              onRemovido={() => {
                if (editando?.id === g.id) setEditando(null);
                carregar();
              }}
            />
          ))}

          {semGrupo.length > 0 && (
            <div style={s.card}>
              <h3 style={s.cardTitulo}>Lojas sem grupo ({semGrupo.length})</h3>
              <p style={{ ...s.suave, marginTop: 0 }}>
                Os alertas destas lojas vão para o grupo admin até você colocá-las em um grupo.
              </p>
              <div style={{ display: "flex", flexWrap: "wrap", gap: "6px" }}>
                {semGrupo.map((l) => (
                  <span key={l.merchantId} style={pilula(cores.amarelo)}>
                    {l.nome}
                  </span>
                ))}
              </div>
            </div>
          )}
        </div>

        {editando && (
          <FormGrupo
            key={editando.id ?? "novo"}
            inicial={editando}
            lojas={lojas}
            onCancelar={() => setEditando(null)}
            onSalvo={() => {
              setEditando(null);
              carregar();
            }}
          />
        )}
      </div>
    </div>
  );
}

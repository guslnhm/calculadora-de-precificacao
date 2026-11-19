import { useMemo, useState } from "react";
import { acessosApi } from "../../services/api";
import { botao, caixa, campo, cor } from "../painel/fmt";
import { pilula } from "../monitor/ui";
import SeletorLojas from "./SeletorLojas";
import { botaoSec, copiar, dataBr, linkNovaSenha, perfilInfo, quandoAcessou } from "./apoio";

// Usuários: lojas de cada um, ativar/desativar e link de nova senha.

export default function AbaUsuarios({ usuarios, lojas, onMudou }) {
  const [busca, setBusca] = useState("");
  const [filtro, setFiltro] = useState("TODOS");
  const [editando, setEditando] = useState(null); // { id, lojas }
  const [linkSenha, setLinkSenha] = useState(null); // { id, link }
  const [erro, setErro] = useState("");

  const lista = useMemo(() => {
    const b = busca.trim().toLowerCase();
    return (usuarios || []).filter((u) => {
      if (filtro === "MENTORADO" && u.perfil !== "MENTORADO") return false;
      if (filtro === "EQUIPE" && u.perfil === "MENTORADO") return false;
      if (filtro === "INATIVOS" && u.ativo) return false;
      if (!b) return true;
      return [u.nome, u.email, ...u.lojas.map((l) => l.nome)].some((t) => (t || "").toLowerCase().includes(b));
    });
  }, [usuarios, busca, filtro]);

  async function salvar(id, dados) {
    setErro("");
    try {
      await acessosApi.editarUsuario(id, dados);
      setEditando(null);
      onMudou();
    } catch (e) {
      setErro(e.message);
    }
  }

  async function gerarLink(u) {
    setErro("");
    try {
      const r = await acessosApi.linkSenha(u.id);
      setLinkSenha({ id: u.id, link: linkNovaSenha(r.token) });
    } catch (e) {
      setErro(e.message);
    }
  }

  return (
    <div style={{ display: "grid", gap: 12, gridTemplateColumns: "minmax(0, 1fr)" }}>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
        <input style={{ ...campo, flex: "1 1 220px" }} placeholder="Buscar por nome, e-mail ou loja..." value={busca}
               onChange={(e) => setBusca(e.target.value)} />
        <select style={campo} value={filtro} onChange={(e) => setFiltro(e.target.value)}>
          <option value="TODOS">Todos</option>
          <option value="MENTORADO">Mentorados</option>
          <option value="EQUIPE">Equipe</option>
          <option value="INATIVOS">Desativados</option>
        </select>
      </div>
      {erro && <div style={{ color: cor.ruim, fontSize: 13 }}>{erro}</div>}
      {lista.length === 0 && <div style={{ color: cor.textoSuave, fontSize: 13 }}>Ninguém encontrado.</div>}

      {lista.map((u) => {
        const p = perfilInfo(u.perfil);
        const ed = editando?.id === u.id;
        return (
          <div key={u.id} style={{ ...caixa, padding: 14, display: "grid", gap: 8, opacity: u.ativo ? 1 : 0.6 }}>
            <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
              <span style={{ fontWeight: 700, fontSize: 15 }}>{u.nome}</span>
              <span style={pilula(p.cor)}>{p.rotulo}</span>
              {!u.ativo && <span style={pilula(cor.cinza)}>Desativado</span>}
              {u.aniversarioHoje && <span title="Aniversário hoje">🎂</span>}
            </div>
            <div style={{ fontSize: 13, color: cor.textoSuave, display: "flex", gap: 12, flexWrap: "wrap" }}>
              <span>{u.email}</span>
              <span>nascimento: {dataBr(u.dataNascimento)}</span>
              <span>último acesso: {quandoAcessou(u.ultimoAcesso)}</span>
              <span>{u.diasAtivos30} dia(s) de uso em 30 dias</span>
            </div>
            {u.perfil === "MENTORADO" && !ed && (
              <div style={{ fontSize: 13 }}>
                <span style={{ color: cor.textoSuave }}>Lojas: </span>
                {u.lojas.length ? u.lojas.map((l) => l.nome).join(", ") : <span style={{ color: cor.amarelo }}>nenhuma (não vê nada)</span>}
              </div>
            )}
            {ed && (
              <div style={{ display: "grid", gap: 8 }}>
                <SeletorLojas lojas={lojas} selecionadas={editando.lojas} onMudar={(l) => setEditando({ ...editando, lojas: l })} />
                <div style={{ display: "flex", gap: 8 }}>
                  <button style={botao} onClick={() => salvar(u.id, { lojas: editando.lojas })}>Salvar lojas</button>
                  <button style={botaoSec} onClick={() => setEditando(null)}>Cancelar</button>
                </div>
              </div>
            )}
            {linkSenha?.id === u.id && (
              <div style={{ display: "grid", gap: 6, background: cor.campo, borderRadius: 8, padding: 10 }}>
                <div style={{ fontSize: 12, color: cor.textoSuave }}>Link de nova senha (vale 48 horas, uma vez só). Mande para a pessoa:</div>
                <code style={{ fontSize: 12, wordBreak: "break-all" }}>{linkSenha.link}</code>
                <div><button style={botaoSec} onClick={() => copiar(linkSenha.link)}>Copiar link</button></div>
              </div>
            )}
            {!ed && (
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                {u.perfil === "MENTORADO" && (
                  <button style={botaoSec} onClick={() => setEditando({ id: u.id, lojas: u.lojas.map((l) => l.chave) })}>Editar lojas</button>
                )}
                <button style={botaoSec} onClick={() => gerarLink(u)}>Link de nova senha</button>
                <button style={{ ...botaoSec, color: u.ativo ? cor.ruim : cor.verde }}
                        onClick={() => {
                          if (!u.ativo || window.confirm(`Desativar o acesso de ${u.nome}? A pessoa sai do site na hora.`)) {
                            salvar(u.id, { ativo: !u.ativo });
                          }
                        }}>
                  {u.ativo ? "Desativar" : "Reativar"}
                </button>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

import { useState } from "react";
import { acessosApi } from "../../services/api";
import { botao, caixa, campo, cor } from "../painel/fmt";
import { pilula } from "../monitor/ui";
import SeletorLojas from "./SeletorLojas";
import { botaoSec, copiar, linkConvite, perfilInfo, quandoAcessou } from "./apoio";

// Criar link de convite (escolhendo as lojas) e acompanhar os convites já feitos.

const SITUACOES = {
  ATIVO: { rotulo: "Aguardando cadastro", cor: cor.azul },
  ESGOTADO: { rotulo: "Usado", cor: cor.verde },
  VENCIDO: { rotulo: "Vencido", cor: cor.cinza },
  REVOGADO: { rotulo: "Cancelado", cor: cor.cinza },
};

function mensagemWhatsapp(convite) {
  const lojas = convite.lojas.map((l) => l.nome).join(", ");
  return `Oi! Liberamos o seu acesso ao painel de resultados${lojas ? ` da ${lojas}` : ""}. `
    + `Lá você acompanha faturamento, pedidos, ticket médio e mais, atualizados todo dia.\n\n`
    + `É só abrir o link e criar o seu acesso:\n${linkConvite(convite.token)}`;
}

export default function AbaConvites({ lojas, convites, onMudou }) {
  const [form, setForm] = useState({ lojas: [], descricao: "", perfil: "MENTORADO", validadeDias: 7, maxUsos: 1 });
  const [erro, setErro] = useState("");
  const [criado, setCriado] = useState(null);
  const [copiado, setCopiado] = useState("");
  const [enviando, setEnviando] = useState(false);

  async function criar(e) {
    e.preventDefault();
    setErro("");
    setEnviando(true);
    try {
      const c = await acessosApi.criarConvite({
        ...form, validadeDias: Number(form.validadeDias), maxUsos: Number(form.maxUsos),
      });
      setCriado(c);
      setForm((f) => ({ ...f, lojas: [], descricao: "" }));
      onMudou();
    } catch (e2) {
      setErro(e2.message);
    } finally {
      setEnviando(false);
    }
  }

  async function copiarTexto(id, texto) {
    await copiar(texto);
    setCopiado(id);
    setTimeout(() => setCopiado(""), 2000);
  }

  async function revogar(c) {
    if (!window.confirm(`Cancelar o link "${c.descricao || c.lojas.map((l) => l.nome).join(", ")}"? Quem não se cadastrou não vai conseguir usar.`)) return;
    try {
      await acessosApi.revogarConvite(c.id);
      onMudou();
    } catch (e2) {
      setErro(e2.message);
    }
  }

  const mentorado = form.perfil === "MENTORADO";

  return (
    <div style={{ display: "grid", gap: 16, gridTemplateColumns: "minmax(0, 1fr)" }}>
      <form onSubmit={criar} style={{ ...caixa, display: "grid", gap: 14 }}>
        <div style={{ fontWeight: 700, fontSize: 16 }}>Novo link de acesso</div>
        <div style={{ display: "grid", gap: 12, gridTemplateColumns: "repeat(auto-fit, minmax(min(200px, 100%), 1fr))" }}>
          <label style={rotulo}>Para quem
            <select style={campo} value={form.perfil} onChange={(e) => setForm({ ...form, perfil: e.target.value })}>
              <option value="MENTORADO">Mentorado (só o painel das lojas escolhidas)</option>
              <option value="EQUIPE">Equipe (acesso a tudo)</option>
            </select>
          </label>
          <label style={rotulo}>Nome do link (opcional)
            <input style={campo} placeholder="ex.: Maria — Brubs" value={form.descricao}
                   onChange={(e) => setForm({ ...form, descricao: e.target.value })} />
          </label>
          <label style={rotulo}>Vale por
            <select style={campo} value={form.validadeDias} onChange={(e) => setForm({ ...form, validadeDias: e.target.value })}>
              {[1, 3, 7, 15, 30].map((d) => <option key={d} value={d}>{d} {d === 1 ? "dia" : "dias"}</option>)}
            </select>
          </label>
          <label style={rotulo}>Quantas pessoas podem usar
            <select style={campo} value={form.maxUsos} onChange={(e) => setForm({ ...form, maxUsos: e.target.value })}>
              {[1, 2, 3, 4, 5, 10].map((n) => <option key={n} value={n}>{n === 1 ? "1 pessoa" : `até ${n} pessoas`}</option>)}
            </select>
          </label>
        </div>
        {mentorado && (
          <div style={{ display: "grid", gap: 6 }}>
            <div style={{ fontSize: 13, color: cor.textoSuave }}>Lojas que a pessoa vai ver</div>
            <SeletorLojas lojas={lojas} selecionadas={form.lojas} onMudar={(l) => setForm({ ...form, lojas: l })} />
          </div>
        )}
        {erro && <div style={{ color: cor.ruim, fontSize: 13 }}>{erro}</div>}
        <div>
          <button style={botao} disabled={enviando || (mentorado && form.lojas.length === 0)}>
            {enviando ? "Gerando..." : "Gerar link"}
          </button>
        </div>
      </form>

      {criado && (
        <div style={{ ...caixa, borderColor: `${cor.verde}88`, display: "grid", gap: 10 }}>
          <div style={{ fontWeight: 700 }}>Link criado ✓</div>
          <code style={{ background: cor.campo, padding: "8px 10px", borderRadius: 8, fontSize: 13, wordBreak: "break-all" }}>
            {linkConvite(criado.token)}
          </code>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <button style={botao} onClick={() => copiarTexto("novo-link", linkConvite(criado.token))}>
              {copiado === "novo-link" ? "Copiado ✓" : "Copiar link"}
            </button>
            <button style={botaoSec} onClick={() => copiarTexto("novo-msg", mensagemWhatsapp(criado))}>
              {copiado === "novo-msg" ? "Copiado ✓" : "Copiar mensagem para o WhatsApp"}
            </button>
          </div>
        </div>
      )}

      <div style={{ display: "grid", gap: 10 }}>
        <div style={{ fontWeight: 700 }}>Links criados</div>
        {convites.length === 0 && <div style={{ color: cor.textoSuave, fontSize: 13 }}>Nenhum link ainda.</div>}
        {convites.map((c) => {
          const s = SITUACOES[c.situacao] || SITUACOES.VENCIDO;
          const p = perfilInfo(c.perfil);
          return (
            <div key={c.id} style={{ ...caixa, padding: 12, display: "grid", gap: 6 }}>
              <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                <span style={pilula(s.cor)}>{s.rotulo}</span>
                <span style={pilula(p.cor)}>{p.rotulo}</span>
                <span style={{ fontWeight: 600 }}>{c.descricao || c.lojas.map((l) => l.nome).join(", ") || "Equipe"}</span>
              </div>
              {c.descricao && c.lojas.length > 0 && (
                <div style={{ fontSize: 13, color: cor.textoSuave }}>{c.lojas.map((l) => l.nome).join(", ")}</div>
              )}
              <div style={{ fontSize: 12, color: cor.textoSuave }}>
                {c.usos}/{c.maxUsos} cadastro(s)
                {c.usadoPor?.length ? ` · ${c.usadoPor.join(", ")}` : ""}
                {" · criado "}{quandoAcessou(c.criadoEm)}{c.criadoPor ? ` por ${c.criadoPor}` : ""}
                {c.situacao === "ATIVO" ? ` · vence ${new Date(c.expiraEm).toLocaleDateString("pt-BR")}` : ""}
              </div>
              {c.situacao === "ATIVO" && (
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                  <button style={botaoSec} onClick={() => copiarTexto(`l${c.id}`, linkConvite(c.token))}>
                    {copiado === `l${c.id}` ? "Copiado ✓" : "Copiar link"}
                  </button>
                  <button style={botaoSec} onClick={() => copiarTexto(`m${c.id}`, mensagemWhatsapp(c))}>
                    {copiado === `m${c.id}` ? "Copiado ✓" : "Copiar mensagem"}
                  </button>
                  <button style={{ ...botaoSec, color: cor.ruim }} onClick={() => revogar(c)}>Cancelar link</button>
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}

const rotulo = { display: "grid", gap: 6, fontSize: 13, color: cor.textoSuave };

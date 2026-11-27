import { useCallback, useEffect, useMemo, useState } from "react";
import { acessosApi, API_BASE_URL } from "../../services/api";
import { botao, caixa, campo, cor } from "../painel/fmt";
import { pilula } from "../monitor/ui";
import SeletorLojas from "./SeletorLojas";
import { botaoSec, copiar, quandoAcessou } from "./apoio";

// Acesso de sistemas de fora (BI de metas de um parceiro etc.) aos números diários do Painel.
// Cada acesso tem a própria chave e só enxerga as lojas marcadas.

const urlVendas = (extra = "") => `${API_BASE_URL}/externo/vendas${extra}`;

function instrucoes(chave) {
  return [
    "Acesso às vendas diárias (iFood) das lojas liberadas para você.",
    "",
    "Endereço (JSON):",
    urlVendas("?de=2026-11-01&ate=2026-11-30"),
    `Cabeçalho: Authorization: Bearer ${chave}`,
    "",
    "Ou como link de CSV (Looker Studio, Excel, Google Sheets):",
    urlVendas(`?formato=csv&chave=${chave}`),
    "(formato=csv-excel para separador ; e vírgula decimal)",
    "",
    "Sem de/ate: do dia 1º do mês até ontem. Máximo 400 dias por chamada. &loja=<merchant_id> filtra uma loja.",
    `Lojas liberadas: ${API_BASE_URL}/externo/lojas`,
    "",
    "Colunas: data, merchant_id, loja, pedidos (não cancelados), cancelados,",
    "faturamento (GMV do iFood: itens + frete pago pelo cliente - descontos bancados pela loja, sem os cancelados),",
    "faturamento_cancelado, ticket_medio, novos_clientes, atualizado_em,",
    "id_loja_portal (id curto da loja, o mesmo dos relatórios do Portal),",
    "valor_itens (itens sem frete e antes dos descontos) e taxa_entrega (taxa cheia), ambos do relatório de pedidos do Portal,",
    "vazios enquanto o relatório do dia não foi carregado; desconto_loja (descontos bancados pela loja, do iFood);",
    "cancelados_pagos e valor_itens_cancelados_pagos (cancelados que o iFood pagou à loja, do relatório).",
    "O iFood completa o dia anterior ao longo da manhã: atualize depois do almoço e releia os últimos 2-3 dias.",
    "",
    "A chave é pessoal: não compartilhe e não coloque em planilha pública.",
  ].join("\n");
}

export default function AbaExterno() {
  const [acessos, setAcessos] = useState([]);
  const [lojasIfood, setLojasIfood] = useState([]);
  const [form, setForm] = useState({ nome: "", lojas: [] });
  const [chave, setChave] = useState(null); // { nome, chave } recém-gerada: só aparece uma vez
  const [editando, setEditando] = useState(null); // { id, lojas }
  const [erro, setErro] = useState("");
  const [copiado, setCopiado] = useState("");
  const [enviando, setEnviando] = useState(false);

  const lojas = useMemo(() => lojasIfood.map((l) => ({ chave: l.merchantId, nome: l.nome })), [lojasIfood]);

  const carregar = useCallback(() => {
    acessosApi.externos()
      .then((r) => {
        setAcessos(r.acessos);
        setLojasIfood(r.lojas);
        setErro("");
      })
      .catch((e) => setErro(e.message));
  }, []);

  useEffect(() => {
    carregar();
  }, [carregar]);

  async function acao(fn) {
    setErro("");
    try {
      await fn();
      carregar();
    } catch (e) {
      setErro(e.message);
    }
  }

  async function criar(e) {
    e.preventDefault();
    setEnviando(true);
    await acao(async () => {
      const r = await acessosApi.criarExterno(form);
      setChave({ nome: r.acesso.nome, chave: r.chave });
      setForm({ nome: "", lojas: [] });
    });
    setEnviando(false);
  }

  async function copiarTexto(id, texto) {
    await copiar(texto);
    setCopiado(id);
    setTimeout(() => setCopiado(""), 2000);
  }

  function trocarChave(a) {
    if (!window.confirm(`Gerar uma chave nova para "${a.nome}"? A chave atual para de funcionar na hora e o BI precisa ser atualizado com a nova.`)) return;
    acao(async () => {
      const r = await acessosApi.novaChaveExterno(a.id);
      setChave({ nome: a.nome, chave: r.chave });
    });
  }

  function excluir(a) {
    if (!window.confirm(`Excluir o acesso "${a.nome}"? A chave para de funcionar e o acesso some da lista.`)) return;
    acao(() => acessosApi.excluirExterno(a.id));
  }

  return (
    <div style={{ display: "grid", gap: 16, gridTemplateColumns: "minmax(0, 1fr)" }}>
      <div style={{ fontSize: 13, color: cor.textoSuave }}>
        Para um sistema de fora (um BI de metas, por exemplo) ler as vendas diárias do iFood só das lojas que você marcar.
        Ele usa uma chave própria e nunca recebe as credenciais do iFood.
      </div>

      <form onSubmit={criar} style={{ ...caixa, display: "grid", gap: 14 }}>
        <div style={{ fontWeight: 700, fontSize: 16 }}>Novo acesso</div>
        <label style={rotulo}>Nome (quem vai usar)
          <input style={campoCheio} placeholder="ex.: BI de metas — Fulano" value={form.nome}
                 onChange={(e) => setForm({ ...form, nome: e.target.value })} />
        </label>
        <div style={{ display: "grid", gap: 6 }}>
          <div style={{ fontSize: 13, color: cor.textoSuave }}>Lojas liberadas</div>
          <SeletorLojas lojas={lojas} selecionadas={form.lojas} onMudar={(l) => setForm({ ...form, lojas: l })} />
        </div>
        <div>
          <button style={botao} disabled={enviando || !form.nome.trim() || form.lojas.length === 0}>
            {enviando ? "Gerando..." : "Gerar chave"}
          </button>
        </div>
      </form>

      {erro && <div style={{ color: cor.ruim, fontSize: 13 }}>{erro}</div>}

      {chave && (
        <div style={{ ...caixa, borderColor: `${cor.verde}88`, display: "grid", gap: 10 }}>
          <div style={{ fontWeight: 700 }}>Chave de “{chave.nome}” ✓</div>
          <div style={{ fontSize: 13, color: cor.amarelo }}>
            Copie agora: por segurança a chave não aparece de novo. Se perder, é só gerar outra.
          </div>
          <code style={{ background: cor.campo, padding: "8px 10px", borderRadius: 8, fontSize: 13, wordBreak: "break-all" }}>
            {chave.chave}
          </code>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <button style={botao} onClick={() => copiarTexto("chave", chave.chave)}>
              {copiado === "chave" ? "Copiado ✓" : "Copiar chave"}
            </button>
            <button style={botaoSec} onClick={() => copiarTexto("instr", instrucoes(chave.chave))}>
              {copiado === "instr" ? "Copiado ✓" : "Copiar instruções para quem vai usar"}
            </button>
            <button style={botaoSec} onClick={() => setChave(null)}>Fechar</button>
          </div>
        </div>
      )}

      <div style={{ display: "grid", gap: 10 }}>
        <div style={{ fontWeight: 700 }}>Acessos criados</div>
        {acessos.length === 0 && <div style={{ color: cor.textoSuave, fontSize: 13 }}>Nenhum acesso ainda.</div>}
        {acessos.map((a) => (
          <div key={a.id} style={{ ...caixa, padding: 12, display: "grid", gap: 8, opacity: a.ativo ? 1 : 0.7 }}>
            <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
              <span style={pilula(a.ativo ? cor.verde : cor.cinza)}>{a.ativo ? "Ativo" : "Desativado"}</span>
              <span style={{ fontWeight: 600 }}>{a.nome}</span>
              <code style={{ fontSize: 12, color: cor.textoSuave }}>{a.inicioChave}</code>
            </div>
            {editando?.id === a.id ? (
              <div style={{ display: "grid", gap: 8 }}>
                <SeletorLojas lojas={lojas} selecionadas={editando.lojas}
                              onMudar={(l) => setEditando({ ...editando, lojas: l })} />
                <div style={{ display: "flex", gap: 8 }}>
                  <button style={botao} disabled={editando.lojas.length === 0}
                          onClick={() => acao(async () => {
                            await acessosApi.editarExterno(a.id, { lojas: editando.lojas });
                            setEditando(null);
                          })}>
                    Salvar lojas
                  </button>
                  <button style={botaoSec} onClick={() => setEditando(null)}>Cancelar</button>
                </div>
              </div>
            ) : (
              <div style={{ fontSize: 13, color: cor.textoSuave }}>
                {a.lojas.length} {a.lojas.length === 1 ? "loja" : "lojas"}: {a.lojas.map((l) => l.nome).join(", ")}
              </div>
            )}
            <div style={{ fontSize: 12, color: cor.textoSuave }}>
              {a.usos > 0 ? `${a.usos} consulta(s) · última ${quandoAcessou(a.ultimoUso)}` : "ainda não foi usado"}
              {" · criado "}{quandoAcessou(a.criadoEm)}{a.criadoPor ? ` por ${a.criadoPor}` : ""}
            </div>
            {editando?.id !== a.id && (
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                <button style={botaoSec} onClick={() => setEditando({ id: a.id, lojas: a.lojas.map((l) => l.merchantId) })}>
                  Mudar lojas
                </button>
                <button style={botaoSec} onClick={() => acao(() => acessosApi.editarExterno(a.id, { ativo: !a.ativo }))}>
                  {a.ativo ? "Desativar" : "Reativar"}
                </button>
                <button style={botaoSec} onClick={() => trocarChave(a)}>Gerar chave nova</button>
                <button style={{ ...botaoSec, color: cor.ruim }} onClick={() => excluir(a)}>Excluir</button>
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

const rotulo = { display: "grid", gap: 6, fontSize: 13, color: cor.textoSuave, minWidth: 0 };
const campoCheio = { ...campo, width: "100%", boxSizing: "border-box" };

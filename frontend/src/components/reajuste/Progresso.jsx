import { useEffect, useState } from "react";
import { reajusteApi } from "../../services/api";
import { cores, dataHora, pilula, s } from "../monitor/ui";
import { reais } from "../cardapio/Controles";

export const SITUACOES_LOTE = {
  PUBLICANDO: { rotulo: "Publicando…", cor: cores.azul },
  CONCLUIDO: { rotulo: "Concluído", cor: cores.verde },
  DESFAZENDO: { rotulo: "Desfazendo…", cor: cores.amarelo },
  DESFEITO: { rotulo: "Desfeito", cor: cores.cinza },
};

const SITUACAO_ITEM = {
  FEITA: { rotulo: "publicado", cor: cores.verde },
  FALHOU: { rotulo: "recusado", cor: cores.vermelho },
  DESFEITA: { rotulo: "desfeito", cor: cores.cinza },
};

/** Os itens que o reajuste mudou: antes → depois (as voltas do "desfazer" aparecem como "desfeito" no item). */
function ItensDoLote({ alteracoes }) {
  const [busca, setBusca] = useState("");
  const linhas = alteracoes.filter((a) => a.desfaz_id == null);
  const lojas = new Set(linhas.map((a) => a.merchant_id)).size;
  const t = busca.trim().toLowerCase();
  const visiveis = linhas.filter((a) => !t || `${a.nome} ${a.loja}`.toLowerCase().includes(t));
  if (!linhas.length) return null;
  return (
    <div>
      {linhas.length > 8 && (
        <input
          style={{ ...s.input, maxWidth: "260px", marginBottom: "8px" }}
          placeholder="Buscar item…"
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
        />
      )}
      <div style={{ ...s.tabelaWrap, maxHeight: "420px", overflowY: "auto" }}>
        <table style={s.tabela}>
          <thead>
            <tr>
              {lojas > 1 && <th style={s.th}>Loja</th>}
              <th style={s.th}>Item</th>
              <th style={{ ...s.th, textAlign: "right" }}>Antes</th>
              <th style={{ ...s.th, textAlign: "right" }}>Depois</th>
              <th style={{ ...s.th, textAlign: "right" }}>Variação</th>
              <th style={s.th}></th>
            </tr>
          </thead>
          <tbody>
            {visiveis.map((a) => {
              const antes = a.antes != null ? Number(a.antes) : null;
              const depois = Number(a.depois);
              const variacao = antes ? ((depois - antes) / antes) * 100 : null;
              const sit = SITUACAO_ITEM[a.situacao] || { rotulo: a.situacao, cor: cores.cinza };
              return (
                <tr key={a.id}>
                  {lojas > 1 && <td style={s.td}>{a.loja}</td>}
                  <td style={s.td}>
                    {a.nome}
                    {a.tipo === "COMPLEMENTO" && <span style={s.suave}> (complemento)</span>}
                    {a.erro && <div style={{ color: cores.vermelho, fontSize: "12px" }}>{a.erro}</div>}
                  </td>
                  <td style={{ ...s.td, textAlign: "right", whiteSpace: "nowrap" }}>{reais(antes)}</td>
                  <td style={{ ...s.td, textAlign: "right", whiteSpace: "nowrap", fontWeight: "bold" }}>{reais(depois)}</td>
                  <td
                    style={{
                      ...s.td,
                      textAlign: "right",
                      whiteSpace: "nowrap",
                      color: variacao == null ? cores.textoSuave : variacao > 0 ? cores.amarelo : cores.azul,
                    }}
                  >
                    {variacao == null ? "—" : `${variacao > 0 ? "+" : ""}${variacao.toFixed(1).replace(".", ",")}%`}
                  </td>
                  <td style={{ ...s.td, textAlign: "right" }}>
                    <span style={pilula(sit.cor)}>{sit.rotulo}</span>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/** Acompanha um lote (publicação ou desfazer) até terminar; mostra o resultado e o botão de desfazer. */
export default function Progresso({ loteId, onFechar }) {
  const [dados, setDados] = useState(null);
  const [erro, setErro] = useState("");
  const [confirmar, setConfirmar] = useState(false);
  const [versao, setVersao] = useState(0);

  useEffect(() => {
    let parar = false;
    let timer;
    async function ler() {
      try {
        const d = await reajusteApi.lote(loteId);
        if (parar) return;
        setDados(d);
        if (["PUBLICANDO", "DESFAZENDO"].includes(d.lote.situacao)) {
          timer = setTimeout(ler, 1500);
        }
      } catch (e) {
        if (!parar) setErro(e.message);
      }
    }
    ler();
    return () => {
      parar = true;
      clearTimeout(timer);
    };
  }, [loteId, versao]);

  async function desfazer() {
    try {
      await reajusteApi.desfazerLote(loteId);
      setConfirmar(false);
      setVersao((v) => v + 1);
    } catch (e) {
      setErro(e.message);
    }
  }

  if (!dados) return <div style={s.vazio}>{erro || "Carregando…"}</div>;
  const l = dados.lote;
  const sit = SITUACOES_LOTE[l.situacao] || { rotulo: l.situacao, cor: cores.cinza };
  const andamento = l.situacao === "DESFAZENDO" ? l.desfeitas : l.feitas + l.falhas;
  const pctFeito = l.total ? Math.min(100, Math.round((andamento / l.total) * 100)) : 0;
  const rodando = ["PUBLICANDO", "DESFAZENDO"].includes(l.situacao);
  const falhas = dados.alteracoes.filter((a) => a.situacao === "FALHOU");

  return (
    <div style={{ ...s.card, display: "grid", gap: "14px" }}>
      <div style={{ display: "flex", gap: "10px", alignItems: "center", flexWrap: "wrap" }}>
        <h3 style={{ margin: 0 }}>Reajuste #{l.id}</h3>
        <span style={pilula(sit.cor)}>{sit.rotulo}</span>
        <span style={s.suave}>
          {dataHora(l.criadoEm)}
          {l.usuario ? ` · por ${l.usuario}` : ""}
        </span>
      </div>
      <div>
        <div style={{ height: "10px", background: cores.campo, borderRadius: "6px", overflow: "hidden" }}>
          <div
            style={{
              height: "100%",
              width: `${rodando ? pctFeito : 100}%`,
              background: l.situacao === "DESFEITO" ? cores.cinza : l.falhas ? cores.amarelo : cores.verde,
              transition: "width 0.4s",
            }}
          />
        </div>
        <div style={{ ...s.suave, marginTop: "6px" }}>
          {l.feitas} de {l.total} preço(s) publicado(s)
          {l.falhas ? ` · ${l.falhas} falha(s)` : ""}
          {l.desfeitas ? ` · ${l.desfeitas} desfeito(s)` : ""}
          {rodando && " · pode sair desta tela, continua rodando no servidor"}
        </div>
      </div>
      {erro && <div style={s.erro}>{erro}</div>}
      {falhas.length > 0 && (
        <div style={{ ...s.aviso, margin: 0 }}>O iFood recusou {falhas.length} preço(s): veja o motivo na lista abaixo.</div>
      )}
      <ItensDoLote alteracoes={dados.alteracoes} />
      <div style={{ display: "flex", gap: "8px", justifyContent: "flex-end", flexWrap: "wrap" }}>
        {l.situacao === "CONCLUIDO" && l.feitas > 0 && !confirmar && (
          <button style={s.botaoPerigo} onClick={() => setConfirmar(true)}>
            Desfazer este reajuste
          </button>
        )}
        {confirmar && (
          <>
            <span style={{ color: cores.amarelo, alignSelf: "center", fontSize: "14px" }}>
              Volta {l.feitas} preço(s) para o valor de antes. Confirma?
            </span>
            <button style={s.botaoPerigo} onClick={desfazer}>
              Sim, desfazer
            </button>
            <button style={s.botaoSec} onClick={() => setConfirmar(false)}>
              Não
            </button>
          </>
        )}
        {onFechar && !rodando && (
          <button style={s.botaoSec} onClick={onFechar}>
            Fechar
          </button>
        )}
      </div>
    </div>
  );
}

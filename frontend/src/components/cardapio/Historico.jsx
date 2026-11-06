import { useEffect, useState } from "react";
import { cardapioApi } from "../../services/api";
import { cores, dataHora, pilula, s } from "../monitor/ui";
import { reais } from "./Controles";

const SITUACOES = {
  FEITA: { rotulo: "Feita", cor: cores.verde },
  FALHOU: { rotulo: "Falhou", cor: cores.vermelho },
  DESFEITA: { rotulo: "Desfeita", cor: cores.cinza },
};

function valor(campo, v) {
  if (v === null || v === undefined) return "—";
  if (campo === "PRECO") return reais(v);
  return v === "UNAVAILABLE" ? "Pausado" : v === "AVAILABLE" ? "Ativo" : v;
}

export default function Historico({ merchantId, versao, onDesfeito }) {
  const [linhas, setLinhas] = useState(null);
  const [erro, setErro] = useState("");
  const [ocupado, setOcupado] = useState(null);
  const [recarregar, setRecarregar] = useState(0);

  useEffect(() => {
    let cancelado = false;
    cardapioApi
      .alteracoes(merchantId)
      .then((d) => !cancelado && (setLinhas(d), setErro("")))
      .catch((e) => !cancelado && setErro(e.message));
    return () => {
      cancelado = true;
    };
  }, [merchantId, versao, recarregar]);

  async function desfazer(a) {
    setOcupado(a.id);
    setErro("");
    try {
      const r = await cardapioApi.desfazer(a.id);
      if (r.situacao === "FALHOU") setErro(`O iFood recusou: ${r.erro}`);
      setRecarregar((v) => v + 1);
      if (onDesfeito) onDesfeito();
    } catch (e) {
      setErro(e.message);
    } finally {
      setOcupado(null);
    }
  }

  return (
    <div style={{ ...s.card, marginBottom: "14px" }}>
      <h3 style={s.cardTitulo}>Histórico de alterações</h3>
      {erro && <div style={s.erro}>{erro}</div>}
      {linhas === null && !erro && <div style={s.suave}>Carregando…</div>}
      {linhas !== null && linhas.length === 0 && (
        <div style={s.suave}>Nenhuma alteração feita por aqui nesta loja ainda.</div>
      )}
      {linhas !== null && linhas.length > 0 && (
        <div style={s.tabelaWrap}>
          <table style={s.tabela}>
            <thead>
              <tr>
                <th style={s.th}>Quando</th>
                <th style={s.th}>Quem</th>
                <th style={s.th}>O quê</th>
                <th style={s.th}>Mudança</th>
                <th style={s.th}></th>
              </tr>
            </thead>
            <tbody>
              {linhas.map((a) => {
                const sit = SITUACOES[a.situacao] || { rotulo: a.situacao, cor: cores.cinza };
                return (
                  <tr key={a.id}>
                    <td style={{ ...s.td, whiteSpace: "nowrap" }}>{dataHora(a.feitoEm)}</td>
                    <td style={s.td}>{a.usuario || "—"}</td>
                    <td style={s.td}>
                      {a.nome}
                      <div style={s.suave}>
                        {a.tipo === "COMPLEMENTO" ? "complemento" : "item"} · {a.campo === "PRECO" ? "preço" : "status"}
                        {a.desfazId ? " · desfazendo outra alteração" : ""}
                      </div>
                    </td>
                    <td style={{ ...s.td, whiteSpace: "nowrap" }}>
                      {valor(a.campo, a.antes)} → <strong>{valor(a.campo, a.depois)}</strong>
                      {a.erro && <div style={{ color: cores.vermelho, fontSize: "12px", whiteSpace: "normal" }}>{a.erro}</div>}
                      {a.situacao === "DESFEITA" && (
                        <div style={s.suave}>
                          desfeita {a.desfeitaPor ? `por ${a.desfeitaPor} ` : ""}em {dataHora(a.desfeitaEm)}
                        </div>
                      )}
                    </td>
                    <td style={{ ...s.td, whiteSpace: "nowrap", textAlign: "right" }}>
                      <span style={{ ...pilula(sit.cor), marginRight: "8px" }}>{sit.rotulo}</span>
                      {a.situacao === "FEITA" && a.antes !== null && (
                        <button
                          style={{ ...s.botaoSec, padding: "4px 10px" }}
                          onClick={() => desfazer(a)}
                          disabled={ocupado === a.id}
                        >
                          {ocupado === a.id ? "…" : "Desfazer"}
                        </button>
                      )}
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

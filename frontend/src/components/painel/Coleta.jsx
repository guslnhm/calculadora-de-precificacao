import { useCallback, useEffect, useState } from "react";
import { painelApi } from "../../services/api";
import { dataHora } from "../monitor/ui";
import { botao } from "./BoletimLoja";
import { caixa, campo } from "./Carteira";
import { cor } from "./fmt";

/** Situação da coleta do Analytics, importação do BI antigo e vínculo das operações do BI com as lojas. */
export default function Coleta() {
  const [status, setStatus] = useState(null);
  const [ops, setOps] = useState([]);
  const [msg, setMsg] = useState("");

  const carregar = useCallback(async () => {
    try {
      const [s, o] = await Promise.all([painelApi.status(), painelApi.operacoesBi()]);
      setStatus(s);
      setOps(o);
    } catch (e) {
      setMsg(e.message);
    }
  }, []);

  useEffect(() => {
    carregar();
  }, [carregar]);

  async function acao(fn, texto) {
    setMsg(texto);
    try {
      const r = await fn();
      setMsg(r?.mensagem || (r?.erro ? r.erro : r?.linhas !== undefined ? `Importado: ${r.linhas} linhas, ${r.vinculadas} operações ligadas, ${r.semVinculo} sem loja` : "Feito"));
      carregar();
    } catch (e) {
      setMsg(e.message);
    }
  }

  async function vincular(id, merchantId) {
    await painelApi.vincularOperacao(id, merchantId);
    carregar();
  }

  if (!status) return <div style={caixa}>{msg || "Carregando..."}</div>;
  const comErro = (status.lojas || []).filter((l) => l.ultimo_erro);
  const ifoodOps = ops.filter((o) => o.plataforma === "IFOOD");

  return (
    <div style={{ display: "grid", gap: 16 }}>
      <div style={caixa}>
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap", alignItems: "center" }}>
          <div style={{ fontSize: 13, lineHeight: 1.7 }}>
            <div>Analytics do iFood: {status.analyticsConfigurado ? "✓ credenciais configuradas" : "✕ falta IFOOD_BI_CLIENT_ID / IFOOD_BI_CLIENT_SECRET"}</div>
            <div>Banco do BI antigo: {status.bancoBiConfigurado ? `✓ configurado · importado em ${dataHora(status.biImportadoEm)}` : "✕ falta BI_DATABASE_URL"}</div>
            <div style={{ color: cor.textoSuave }}>Coleta automática todo dia às 9h40 · {status.coletando ? "coletando agora..." : status.ultimaColeta || "ainda não rodou"}</div>
          </div>
          <div style={{ marginLeft: "auto", display: "flex", gap: 8 }}>
            <button style={botao} onClick={() => acao(painelApi.coletar, "Iniciando coleta...")} disabled={status.coletando}>Coletar agora</button>
            <button style={{ ...botao, background: cor.campo, border: `1px solid ${cor.bordaCampo}` }} onClick={() => acao(painelApi.importarBi, "Importando do BI...")}>Importar BI</button>
          </div>
        </div>
        {msg && <div style={{ marginTop: 10, fontSize: 13, color: cor.textoSuave }}>{msg}</div>}
      </div>

      <div style={caixa}>
        <div style={{ fontWeight: 700, marginBottom: 8 }}>Lojas ({(status.lojas || []).length}) · {comErro.length} com erro</div>
        <div style={{ overflowX: "auto", maxHeight: 420 }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead><tr style={{ color: cor.textoSuave, textAlign: "left" }}>
              <th style={th}>Loja</th><th style={th}>Dados de</th><th style={th}>até</th><th style={th}>Última coleta</th><th style={th}>Erro</th><th />
            </tr></thead>
            <tbody>
              {(status.lojas || []).map((l) => (
                <tr key={l.merchant_id} style={{ borderTop: `1px solid ${cor.grade}` }}>
                  <td style={tdc}>{l.nome}</td>
                  <td style={tdc}>{l.desde || "—"}</td>
                  <td style={tdc}>{l.ate || "—"}</td>
                  <td style={tdc}>{dataHora(l.ultima_coleta)}</td>
                  <td style={{ ...tdc, color: cor.ruim, maxWidth: 360 }}>{l.ultimo_erro || ""}</td>
                  <td style={tdc}><button style={{ ...botao, padding: "4px 8px", fontSize: 12 }} onClick={() => acao(() => painelApi.coletarLoja(l.merchant_id), `Coletando ${l.nome}...`)}>Coletar</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {ifoodOps.length > 0 && (
        <div style={caixa}>
          <div style={{ fontWeight: 700 }}>Operações do BI antigo × lojas do iFood</div>
          <div style={{ fontSize: 12, color: cor.textoSuave, marginBottom: 8 }}>O histórico do Power BI é ligado à loja pelo id ou pelo nome. Corrija aqui o que ficou errado ou sem loja.</div>
          <div style={{ overflowX: "auto", maxHeight: 420 }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
              <tbody>
                {ifoodOps.map((o) => (
                  <tr key={o.idOperacao} style={{ borderTop: `1px solid ${cor.grade}` }}>
                    <td style={tdc}>{o.operacao || o.loja}</td>
                    <td style={{ ...tdc, color: cor.textoSuave }}>{o.metodo || "—"}</td>
                    <td style={tdc}>
                      <select value={o.merchantId || ""} onChange={(e) => vincular(o.idOperacao, e.target.value)} style={{ ...campo, maxWidth: 320 }}>
                        <option value="">— sem loja —</option>
                        {(status.lojas || []).map((l) => <option key={l.merchant_id} value={l.merchant_id}>{l.nome}</option>)}
                      </select>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

const th = { padding: "6px 8px", position: "sticky", top: 0, background: cor.card };
const tdc = { padding: "6px 8px" };

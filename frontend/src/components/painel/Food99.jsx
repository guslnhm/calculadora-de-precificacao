import { useCallback, useEffect, useMemo, useState } from "react";
import { food99Api } from "../../services/api";
import { botao, caixa, campo, cor, dataCurta, inteiro, percentual, reais } from "./fmt";

// 99Food (Open Platform, só leitura): lojas vinculadas ao nosso app, link de autorização para o restaurante,
// coleta do financeiro e os números por loja e dia. A 99 libera os dados com até 2 dias de atraso.

const iso = (d) => d.toISOString().slice(0, 10);

function quando(v) {
  if (!v) return "nunca";
  return new Date(v).toLocaleString("pt-BR", { timeZone: "America/Sao_Paulo", day: "2-digit", month: "2-digit", hour: "2-digit", minute: "2-digit" });
}

export default function Food99() {
  const [sit, setSit] = useState(null);
  const [dias, setDias] = useState([]);
  const [link, setLink] = useState("");
  const [msg, setMsg] = useState("");
  const [erro, setErro] = useState("");
  const hoje = new Date();
  const [de, setDe] = useState(iso(new Date(hoje.getFullYear(), hoje.getMonth(), 1)));
  const [ate, setAte] = useState(iso(new Date(hoje.getTime() - 86400000)));

  const carregar = useCallback(() => {
    setErro("");
    food99Api.situacao().then(setSit).catch((e) => setErro(e.message));
    food99Api.dias(de, ate).then(setDias).catch((e) => setErro(e.message));
  }, [de, ate]);

  useEffect(() => {
    carregar();
  }, [carregar]);

  const porLoja = useMemo(() => {
    const m = new Map();
    for (const d of dias) {
      const a = m.get(d.shopId) || { loja: d.loja, pedidos: 0, cancelados: 0, valorItens: 0, liquido: 0, comissao: 0, taxas: 0, dias: 0 };
      a.pedidos += d.pedidos;
      a.cancelados += d.cancelados;
      a.valorItens += Number(d.valorItens);
      a.liquido += Number(d.liquido);
      a.comissao += Number(d.comissao);
      a.taxas += Number(d.taxaPagamento) + Number(d.taxaEntrega);
      a.dias += 1;
      m.set(d.shopId, a);
    }
    return [...m.values()].sort((x, y) => y.valorItens - x.valorItens);
  }, [dias]);

  async function gerarLink() {
    setErro("");
    try {
      const r = await food99Api.link();
      setLink(r.url);
    } catch (e) {
      setErro(e.message);
    }
  }

  async function coletar(comPeriodo) {
    setErro("");
    try {
      const r = await food99Api.coletar(comPeriodo ? de : undefined, comPeriodo ? ate : undefined);
      setMsg(`${r.mensagem}. Atualize a tela em alguns minutos.`);
      setTimeout(carregar, 3000);
    } catch (e) {
      setErro(e.message);
    }
  }

  if (sit && !sit.configurado) {
    return (
      <div style={{ ...caixa, display: "grid", gap: 8 }}>
        <div style={{ fontWeight: 700 }}>99Food ainda não configurada</div>
        <div style={{ fontSize: 13, color: cor.textoSuave }}>
          Quando a 99 aprovar o nosso app, coloque <code>FOOD99_APP_ID</code> e <code>FOOD99_APP_SECRET</code> nas variáveis do
          Railway. Depois disso aparecem aqui o link de autorização para os restaurantes, as lojas vinculadas e os números
          do financeiro.
        </div>
      </div>
    );
  }

  const vinculadas = (sit?.lojas || []).filter((l) => l.vinculada);

  return (
    <div style={{ display: "grid", gap: 16, gridTemplateColumns: "minmax(0, 1fr)" }}>
      <div style={{ ...caixa, display: "grid", gap: 10 }}>
        <div style={{ fontWeight: 700 }}>Vincular loja</div>
        <div style={{ fontSize: 13, color: cor.textoSuave }}>
          Gere o link e mande para o dono da loja (precisa ser o super-admin da loja na 99). Ele entra, escolhe a loja
          certa e clica em Autorizar. O link vale 7 dias e não mexe no PDV nem nos pedidos.
        </div>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
          <button style={botao} onClick={gerarLink}>Gerar link de autorização</button>
          {link && (
            <button style={{ ...botao, background: cor.campo, border: `1px solid ${cor.bordaCampo}` }}
                    onClick={() => navigator.clipboard?.writeText(link)}>Copiar link</button>
          )}
        </div>
        {link && <code style={{ fontSize: 12, wordBreak: "break-all", color: cor.textoSuave }}>{link}</code>}
      </div>

      <div style={{ ...caixa, display: "grid", gap: 10 }}>
        <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
          <div style={{ fontWeight: 700 }}>Lojas no app ({vinculadas.length} vinculada{vinculadas.length === 1 ? "" : "s"})</div>
          <button style={{ ...botao, marginLeft: "auto" }} onClick={() => coletar(false)} disabled={sit?.rodando}>
            {sit?.rodando ? "Coletando..." : "Coletar últimos dias"}
          </button>
        </div>
        {msg && <div style={{ fontSize: 13, color: cor.verde }}>{msg}</div>}
        {(sit?.lojas || []).length === 0 && <div style={{ fontSize: 13, color: cor.textoSuave }}>Nenhuma loja ainda. Rode uma coleta depois que a loja autorizar.</div>}
        {(sit?.lojas || []).map((l) => (
          <div key={l.shop_id} style={{ fontSize: 13, display: "flex", gap: 8, flexWrap: "wrap" }}>
            <span style={{ fontWeight: 600 }}>{l.nome || l.shop_id}</span>
            <span style={{ color: l.vinculada ? cor.verde : cor.textoSuave }}>{l.vinculada ? "vinculada" : "autorizou, sem vínculo"}</span>
            <span style={{ color: cor.textoSuave }}>· coleta {quando(l.ultima_coleta)}{l.ultimo_dia ? ` · dados até ${dataCurta(l.ultimo_dia)}` : ""}</span>
            {l.erro && <span style={{ color: cor.ruim }}>· {l.erro}</span>}
          </div>
        ))}
      </div>

      <div style={{ ...caixa, display: "grid", gap: 10 }}>
        <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
          <div style={{ fontWeight: 700 }}>Financeiro por loja</div>
          <input type="date" style={campo} value={de} onChange={(e) => setDe(e.target.value)} />
          <span style={{ color: cor.textoSuave }}>a</span>
          <input type="date" style={campo} value={ate} onChange={(e) => setAte(e.target.value)} />
          <button style={{ ...botao, background: cor.campo, border: `1px solid ${cor.bordaCampo}` }} onClick={() => coletar(true)}>
            Recoletar este período
          </button>
        </div>
        <div style={{ fontSize: 12, color: cor.textoSuave }}>
          Valores da 99 (Get Bill Data). A 99 libera com até 2 dias de atraso. Ainda em conferência contra o Portal da 99 na loja teste.
        </div>
        {erro && <div style={{ color: cor.ruim, fontSize: 13 }}>{erro}</div>}
        {porLoja.length === 0 ? (
          <div style={{ fontSize: 13, color: cor.textoSuave }}>Sem dados no período.</div>
        ) : (
          <div style={{ overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
              <thead>
                <tr style={{ color: cor.textoSuave, textAlign: "right" }}>
                  <th style={{ textAlign: "left", padding: 6 }}>Loja</th>
                  <th style={{ padding: 6 }}>Pedidos</th>
                  <th style={{ padding: 6 }}>Cancelados</th>
                  <th style={{ padding: 6 }}>Valor dos itens</th>
                  <th style={{ padding: 6 }}>Comissão</th>
                  <th style={{ padding: 6 }}>Taxas</th>
                  <th style={{ padding: 6 }}>Líquido</th>
                  <th style={{ padding: 6 }}>Custo 99</th>
                </tr>
              </thead>
              <tbody>
                {porLoja.map((l) => (
                  <tr key={l.loja} style={{ borderTop: `1px solid ${cor.borda}`, textAlign: "right" }}>
                    <td style={{ textAlign: "left", padding: 6 }}>{l.loja}</td>
                    <td style={{ padding: 6 }}>{inteiro(l.pedidos)}</td>
                    <td style={{ padding: 6 }}>{inteiro(l.cancelados)}</td>
                    <td style={{ padding: 6 }}>{reais(l.valorItens)}</td>
                    <td style={{ padding: 6 }}>{reais(l.comissao)}</td>
                    <td style={{ padding: 6 }}>{reais(l.taxas)}</td>
                    <td style={{ padding: 6 }}>{reais(l.liquido)}</td>
                    <td style={{ padding: 6 }}>{l.valorItens > 0 ? percentual(1 - l.liquido / l.valorItens) : "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

import { useRef, useState } from "react";
import { painelApi } from "../../services/api";
import { botao, caixa, cor } from "./fmt";

// Envio do "Relatório de pedidos" do Portal do Parceiro para a base histórica (pedidos → silver → gold).

function dataBr(iso) {
  if (!iso) return "—";
  const [a, m, d] = iso.split("-");
  return `${d}/${m}/${a}`;
}

export default function EnviarRelatorio() {
  const entrada = useRef(null);
  const [arquivo, setArquivo] = useState(null);
  const [enviando, setEnviando] = useState(false);
  const [resultado, setResultado] = useState(null);
  const [erro, setErro] = useState("");

  async function enviar() {
    if (!arquivo) return;
    setEnviando(true);
    setErro("");
    setResultado(null);
    try {
      setResultado(await painelApi.enviarRelatorio(arquivo));
      setArquivo(null);
      if (entrada.current) entrada.current.value = "";
    } catch (e) {
      setErro(e.message);
    } finally {
      setEnviando(false);
    }
  }

  const r = resultado;
  return (
    <div style={{ ...caixa, display: "grid", gap: 10 }}>
      <div>
        <div style={{ fontWeight: 700 }}>Base histórica · relatório de pedidos</div>
        <div style={{ fontSize: 13, color: cor.textoSuave, marginTop: 4, lineHeight: 1.5 }}>
          Envie o <strong>.xlsx do jeito que o Portal do Parceiro exporta</strong> (Relatórios → Pedidos), sem tirar colunas.
          O site grava os pedidos na base, atualiza a silver e refaz a gold das semanas e meses do arquivo. Pedido que já
          estava na base é substituído, então pode reenviar um período sem duplicar. O .csv do processo antigo também é aceito.
        </div>
      </div>
      <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        <input ref={entrada} type="file" accept=".xlsx,.csv" onChange={(e) => setArquivo(e.target.files?.[0] || null)}
               style={{ fontSize: 13, maxWidth: "100%" }} />
        <button style={botao} onClick={enviar} disabled={!arquivo || enviando}>
          {enviando ? "Enviando e processando…" : "Enviar para a base"}
        </button>
      </div>
      {enviando && <div style={{ fontSize: 13, color: cor.textoSuave }}>Um relatório de semana leva alguns segundos; de mês, até um minuto.</div>}
      {erro && <div style={{ fontSize: 13, color: cor.ruim }}>{erro}</div>}
      {r && (
        <div style={{ fontSize: 13, lineHeight: 1.7, background: cor.campo, borderRadius: 8, padding: "10px 12px" }}>
          <div style={{ color: cor.bom, fontWeight: 600 }}>✓ Base atualizada</div>
          <div>
            {r.gravados.toLocaleString("pt-BR")} pedido(s) de {dataBr(r.de)} a {dataBr(r.ate)} gravados ·{" "}
            {r.noFato.toLocaleString("pt-BR")} nas lojas cadastradas · {r.semanas} semana(s) e {r.meses} mês(es) recalculados na gold
          </div>
          {r.ignorados > 0 && <div style={{ color: cor.textoSuave }}>{r.ignorados} linha(s) sem ID, loja ou data foram ignoradas.</div>}
          <div style={{ color: cor.textoSuave }}>O painel reimporta a gold sozinho em seguida.</div>
          {r.semOperacao?.length > 0 && (
            <div style={{ marginTop: 6 }}>
              <div style={{ color: cor.ruim, fontWeight: 600 }}>
                {r.semOperacao.length} loja(s) sem operação cadastrada na base (os pedidos ficaram só na tabela pedidos):
              </div>
              {r.semOperacao.map((l) => (
                <div key={l.idLoja}>• {l.nome} (ID {l.idLoja}) · {l.pedidos} pedido(s)</div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

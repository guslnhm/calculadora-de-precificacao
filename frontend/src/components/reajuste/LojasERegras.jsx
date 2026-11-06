import { useEffect, useMemo, useState } from "react";
import { reajusteApi } from "../../services/api";
import { cores, pilula, s } from "../monitor/ui";
import ItensDoIfood from "./ItensDoIfood";

export const MODOS = {
  BASE: "Preço base (sem cupom)",
  CUPOM_5: "Base + R$ 5 (cupom de R$ 5)",
  CUPOM_8: "Base + R$ 8 (cupom de R$ 8)",
  CUPOM_10: "Base + R$ 10 (cupom de R$ 10)",
};
export const ARREDONDAMENTOS = {
  NENHUM: "Sem arredondar",
  NOVENTA: "Terminar em ,90",
  NOVENTA_E_NOVE: "Terminar em ,99",
};

function numeroBR(v) {
  return Number(v || 0).toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export default function LojasERegras({ lojaId, dados, onSalvo }) {
  // o componente é recriado (key) quando os dados mudam, então dá para iniciar direto deles
  const [regras, setRegras] = useState(() => ({
    modoCupom: dados.regras.modoCupom,
    arredondamento: dados.regras.arredondamento,
    fretePadrao: String(dados.regras.fretePadrao ?? 0).replace(".", ","),
  }));
  const [todas, setTodas] = useState(null);
  const [marcadas, setMarcadas] = useState(() => dados.lojasIfood.map((l) => l.merchantId));
  const [busca, setBusca] = useState("");
  const [msg, setMsg] = useState(null);
  const [salvando, setSalvando] = useState("");

  useEffect(() => {
    reajusteApi
      .todasLojasIfood()
      .then(setTodas)
      .catch((e) => setMsg({ tipo: "erro", texto: e.message }));
  }, []);

  const filtradas = useMemo(() => {
    const t = busca.trim().toLowerCase();
    return (todas || []).filter((l) => !t || String(l.nome).toLowerCase().includes(t));
  }, [todas, busca]);

  async function salvarRegras(e) {
    e.preventDefault();
    const frete = Number(String(regras.fretePadrao).replace(",", "."));
    if (Number.isNaN(frete) || frete < 0) {
      setMsg({ tipo: "erro", texto: "Frete inválido" });
      return;
    }
    setSalvando("regras");
    try {
      await reajusteApi.salvarRegras(lojaId, { ...regras, fretePadrao: frete });
      setMsg({ tipo: "ok", texto: "Regras salvas." });
      onSalvo();
    } catch (e2) {
      setMsg({ tipo: "erro", texto: e2.message });
    } finally {
      setSalvando("");
    }
  }

  async function salvarLojas() {
    setSalvando("lojas");
    try {
      await reajusteApi.salvarLojasIfood(lojaId, marcadas);
      setMsg({ tipo: "ok", texto: "Lojas do iFood salvas." });
      onSalvo();
      reajusteApi.todasLojasIfood().then(setTodas);
    } catch (e) {
      setMsg({ tipo: "erro", texto: e.message });
    } finally {
      setSalvando("");
    }
  }

  return (
    <div>
      {msg && <div style={msg.tipo === "ok" ? s.ok : s.erro}>{msg.texto}</div>}
      <div style={{ ...s.card, marginBottom: "16px" }}>
        <h3 style={s.cardTitulo}>Itens do iFood e planilha de custos</h3>
        <ItensDoIfood lojaId={lojaId} nomeLoja={dados.regras.nome} />
      </div>
      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))",
          gap: "16px",
          alignItems: "start",
        }}
      >
        <form onSubmit={salvarRegras} style={{ ...s.card, display: "grid", gap: "14px" }}>
          <h3 style={{ ...s.cardTitulo, margin: 0 }}>Regras de preço no iFood</h3>
          <p style={{ ...s.suave, margin: 0 }}>
            O preço novo sai da calculadora: (CMV + frete) ÷ (1 − {numeroBR(dados.regras.percentualTotal)}% dos
            percentuais da loja), mais o cupom e o arredondamento abaixo.
          </p>
          <label style={s.label}>
            Preço que vai para o iFood
            <select
              style={s.input}
              value={regras.modoCupom}
              onChange={(e) => setRegras({ ...regras, modoCupom: e.target.value })}
            >
              {Object.entries(MODOS).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </label>
          <label style={s.label}>
            Arredondamento
            <select
              style={s.input}
              value={regras.arredondamento}
              onChange={(e) => setRegras({ ...regras, arredondamento: e.target.value })}
            >
              {Object.entries(ARREDONDAMENTOS).map(([k, v]) => (
                <option key={k} value={k}>
                  {v}
                </option>
              ))}
            </select>
          </label>
          <label style={s.label}>
            Frete padrão somado ao CMV (R$)
            <input
              style={s.input}
              inputMode="decimal"
              value={regras.fretePadrao}
              onChange={(e) => setRegras({ ...regras, fretePadrao: e.target.value })}
            />
            <span style={{ fontSize: "12px" }}>Quase sempre 0. Um item com frete próprio na calculadora usa o dele.</span>
          </label>
          <div style={{ display: "flex", justifyContent: "flex-end" }}>
            <button type="submit" style={s.botao} disabled={salvando === "regras"}>
              {salvando === "regras" ? "Salvando…" : "Salvar regras"}
            </button>
          </div>
        </form>

        <div style={{ ...s.card, display: "grid", gap: "12px" }}>
          <h3 style={{ ...s.cardTitulo, margin: 0 }}>Lojas no iFood que usam estes preços</h3>
          <p style={{ ...s.suave, margin: 0 }}>
            Marque todas as lojas do iFood com o mesmo cardápio e custos desta loja da calculadora (por exemplo, as
            unidades de uma rede).
          </p>
          <input style={s.input} placeholder="Buscar loja do iFood…" value={busca} onChange={(e) => setBusca(e.target.value)} />
          <div
            style={{
              maxHeight: "300px",
              overflowY: "auto",
              border: `1px solid ${cores.bordaCampo}`,
              borderRadius: "10px",
              padding: "6px 10px",
            }}
          >
            {todas === null && <div style={s.suave}>Carregando…</div>}
            {todas !== null && todas.length === 0 && (
              <div style={s.suave}>Nenhuma loja do iFood ainda. Elas aparecem quando autorizam o aplicativo.</div>
            )}
            {filtradas.map((l) => {
              const outra = l.lojaId && l.lojaId !== lojaId;
              return (
                <label key={l.merchantId} style={{ ...s.check, padding: "6px 0" }}>
                  <input
                    type="checkbox"
                    checked={marcadas.includes(l.merchantId)}
                    onChange={(e) =>
                      setMarcadas((m) => (e.target.checked ? [...m, l.merchantId] : m.filter((x) => x !== l.merchantId)))
                    }
                  />
                  <span>{l.nome}</span>
                  {outra && (
                    <span style={pilula(cores.amarelo)} title="Se marcar aqui, ela sai da outra loja da calculadora">
                      ligada a outra loja
                    </span>
                  )}
                </label>
              );
            })}
          </div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span style={s.suave}>{marcadas.length} marcada(s)</span>
            <button style={s.botao} onClick={salvarLojas} disabled={salvando === "lojas"}>
              {salvando === "lojas" ? "Salvando…" : "Salvar lojas"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

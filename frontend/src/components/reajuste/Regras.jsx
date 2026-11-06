import { useEffect, useMemo, useState } from "react";
import { reajusteApi } from "../../services/api";
import { cores, pilula, s } from "../monitor/ui";

export const MODOS = {
  BASE: "Base (sem cupom)",
  CUPOM_5: "Base + R$ 5",
  CUPOM_8: "Base + R$ 8",
  CUPOM_10: "Base + R$ 10",
};
export const ARREDONDAMENTOS = {
  NENHUM: "Sem arredondar",
  NOVENTA: "Terminar em ,90",
  NOVENTA_E_NOVE: "Terminar em ,99",
};

/** Regras de cálculo da loja numa linha só (fica no topo de "Calcular preços"). */
export function RegrasBar({ lojaId, regras, onSalvo }) {
  const [form, setForm] = useState(() => ({
    modoCupom: regras.modoCupom,
    arredondamento: regras.arredondamento,
    fretePadrao: String(regras.fretePadrao ?? 0).replace(".", ","),
  }));
  const [msg, setMsg] = useState(null);
  const [salvando, setSalvando] = useState(false);

  const mudou =
    form.modoCupom !== regras.modoCupom ||
    form.arredondamento !== regras.arredondamento ||
    Number(String(form.fretePadrao).replace(",", ".")) !== Number(regras.fretePadrao || 0);

  async function salvar() {
    const frete = Number(String(form.fretePadrao).replace(",", "."));
    if (Number.isNaN(frete) || frete < 0) {
      setMsg({ tipo: "erro", texto: "Frete inválido" });
      return;
    }
    setSalvando(true);
    setMsg(null);
    try {
      await reajusteApi.salvarRegras(lojaId, { ...form, fretePadrao: frete });
      if (onSalvo) onSalvo();
    } catch (e) {
      setMsg({ tipo: "erro", texto: e.message });
    } finally {
      setSalvando(false);
    }
  }

  return (
    <div style={{ ...s.card, padding: "14px 18px", marginBottom: "14px", display: "grid", gap: "10px" }}>
      <div style={{ display: "flex", gap: "12px", flexWrap: "wrap", alignItems: "flex-end" }}>
        <label style={{ ...s.label, flex: "1 1 170px" }}>
          Frete somado ao custo (R$)
          <input
            style={s.input}
            inputMode="decimal"
            value={form.fretePadrao}
            onChange={(e) => setForm({ ...form, fretePadrao: e.target.value })}
          />
        </label>
        <label style={{ ...s.label, flex: "1 1 170px" }}>
          Arredondamento
          <select style={s.input} value={form.arredondamento} onChange={(e) => setForm({ ...form, arredondamento: e.target.value })}>
            {Object.entries(ARREDONDAMENTOS).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        <label style={{ ...s.label, flex: "1 1 170px" }}>
          Cupom para item ainda sem preço salvo
          <select style={s.input} value={form.modoCupom} onChange={(e) => setForm({ ...form, modoCupom: e.target.value })}>
            {Object.entries(MODOS).map(([k, v]) => (
              <option key={k} value={k}>
                {v}
              </option>
            ))}
          </select>
        </label>
        <button style={s.botao} onClick={salvar} disabled={!mudou || salvando}>
          {salvando ? "Salvando…" : "Salvar regras"}
        </button>
      </div>
      <div style={s.suave}>
        Preço calculado = (custo + frete) ÷ (1 − {Number(regras.percentualTotal).toFixed(1).replace(".", ",")}% dos
        percentuais da loja) + cupom. Os percentuais ficam na página Lojas.
      </div>
      {msg && <div style={{ ...s.erro, margin: 0 }}>{msg.texto}</div>}
    </div>
  );
}

/** Quais lojas do iFood usam os preços desta loja da calculadora (rede). */
export function LojasIfoodLigadas({ lojaId, ligadas, onSalvo }) {
  const [todas, setTodas] = useState(null);
  const [marcadas, setMarcadas] = useState(() => ligadas.map((l) => l.merchantId));
  const [busca, setBusca] = useState("");
  const [msg, setMsg] = useState(null);
  const [salvando, setSalvando] = useState(false);

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

  async function salvar() {
    setSalvando(true);
    try {
      await reajusteApi.salvarLojasIfood(lojaId, marcadas);
      if (onSalvo) onSalvo();
    } catch (e) {
      setMsg({ tipo: "erro", texto: e.message });
    } finally {
      setSalvando(false);
    }
  }

  return (
    <div style={{ ...s.card, display: "grid", gap: "10px", marginBottom: "16px" }}>
      <h3 style={{ ...s.cardTitulo, margin: 0 }}>Lojas do iFood ligadas</h3>
      <p style={{ ...s.suave, margin: 0 }}>
        Ao importar os itens do iFood (página Itens) a loja já é ligada. Use aqui para uma rede com o mesmo cardápio e
        custos em várias unidades, ou para desligar uma loja.
      </p>
      <input style={{ ...s.input, maxWidth: "320px" }} placeholder="Buscar loja do iFood…" value={busca} onChange={(e) => setBusca(e.target.value)} />
      <div style={{ maxHeight: "220px", overflowY: "auto", border: `1px solid ${cores.bordaCampo}`, borderRadius: "10px", padding: "6px 10px" }}>
        {todas === null && <div style={s.suave}>Carregando…</div>}
        {filtradas.map((l) => (
          <label key={l.merchantId} style={{ ...s.check, padding: "5px 0" }}>
            <input
              type="checkbox"
              checked={marcadas.includes(l.merchantId)}
              onChange={(e) => setMarcadas((m) => (e.target.checked ? [...m, l.merchantId] : m.filter((x) => x !== l.merchantId)))}
            />
            <span>{l.nome}</span>
            {l.lojaId && l.lojaId !== lojaId && <span style={pilula(cores.amarelo)}>ligada a outra loja</span>}
          </label>
        ))}
      </div>
      {msg && <div style={{ ...s.erro, margin: 0 }}>{msg.texto}</div>}
      <div style={{ display: "flex", justifyContent: "flex-end" }}>
        <button style={s.botao} onClick={salvar} disabled={salvando}>
          {salvando ? "Salvando…" : "Salvar lojas ligadas"}
        </button>
      </div>
    </div>
  );
}

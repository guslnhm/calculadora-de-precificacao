import { useState } from "react";
import { cores, s } from "../monitor/ui";

export function reais(v) {
  if (v === null || v === undefined || v === "") return "—";
  return Number(v).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}

/** "34,90", "34.90", "R$ 34,90" → 34.9 (ou null se inválido). */
export function lerPreco(texto) {
  const limpo = String(texto || "")
    .replace(/[R$\s]/g, "")
    .replace(/\.(?=\d{3}(\D|$))/g, "")
    .replace(",", ".");
  if (!/^\d+(\.\d{1,2})?$/.test(limpo)) return null;
  return Number(limpo);
}

/** Interruptor Ativo/Pausado. */
export function Interruptor({ ativo, ocupado, onMudar, rotulo }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={ativo}
      aria-label={rotulo}
      disabled={ocupado}
      onClick={() => onMudar(!ativo)}
      title={ativo ? "Ativo no iFood. Clique para pausar." : "Pausado no iFood. Clique para ativar."}
      style={{
        display: "inline-flex",
        alignItems: "center",
        gap: "8px",
        background: "transparent",
        border: "none",
        color: ativo ? cores.verde : cores.amarelo,
        cursor: ocupado ? "wait" : "pointer",
        padding: "4px 0",
        fontSize: "13px",
        fontWeight: "bold",
        whiteSpace: "nowrap",
        opacity: ocupado ? 0.6 : 1,
      }}
    >
      <span
        style={{
          width: "34px",
          height: "20px",
          borderRadius: "999px",
          background: ativo ? cores.verde : cores.campo,
          border: `1px solid ${ativo ? cores.verde : cores.bordaCampo}`,
          position: "relative",
          transition: "background 0.15s",
          flexShrink: 0,
        }}
      >
        <span
          style={{
            position: "absolute",
            top: "2px",
            left: ativo ? "16px" : "2px",
            width: "14px",
            height: "14px",
            borderRadius: "50%",
            background: cores.texto,
            transition: "left 0.15s",
          }}
        />
      </span>
      {ativo ? "Ativo" : "Pausado"}
    </button>
  );
}

/** Preço com edição no lugar: clica, digita, Enter salva, Esc cancela. */
export function PrecoEditavel({ valor, precoOriginal, ocupado, onSalvar, permitirZero = false }) {
  const [editando, setEditando] = useState(false);
  const [texto, setTexto] = useState("");
  const [erro, setErro] = useState("");

  function abrir() {
    setTexto(valor === null || valor === undefined ? "" : Number(valor).toFixed(2).replace(".", ","));
    setErro("");
    setEditando(true);
  }

  async function salvar() {
    const v = lerPreco(texto);
    if (v === null || (!permitirZero && v === 0)) {
      setErro(permitirZero ? "Preço inválido" : "Preço inválido (maior que zero)");
      return;
    }
    if (Number(valor) === v) {
      setEditando(false);
      return;
    }
    const ok = await onSalvar(v);
    if (ok) setEditando(false);
  }

  if (!editando) {
    const dePor = precoOriginal && valor && Number(precoOriginal) > Number(valor);
    return (
      <button
        type="button"
        onClick={abrir}
        disabled={ocupado}
        title="Clique para mudar o preço"
        style={{
          background: "transparent",
          border: `1px dashed transparent`,
          borderRadius: "8px",
          color: cores.texto,
          padding: "4px 6px",
          cursor: "pointer",
          fontVariantNumeric: "tabular-nums",
          whiteSpace: "nowrap",
          fontSize: "14px",
          textAlign: "right",
        }}
        onMouseEnter={(e) => (e.currentTarget.style.borderColor = cores.bordaCampo)}
        onMouseLeave={(e) => (e.currentTarget.style.borderColor = "transparent")}
      >
        {dePor && (
          <span style={{ textDecoration: "line-through", color: cores.textoSuave, marginRight: "6px", fontSize: "12px" }}>
            {reais(precoOriginal)}
          </span>
        )}
        {reais(valor)} <span style={{ color: cores.textoSuave, fontSize: "12px" }}>✎</span>
      </button>
    );
  }

  return (
    <span style={{ display: "inline-grid", gap: "4px" }}>
      <span style={{ display: "inline-flex", gap: "4px", alignItems: "center" }}>
        <input
          autoFocus
          inputMode="decimal"
          value={texto}
          onChange={(e) => setTexto(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") salvar();
            if (e.key === "Escape") setEditando(false);
          }}
          style={{ ...s.input, width: "90px", padding: "6px 8px", textAlign: "right" }}
          aria-label="Novo preço"
        />
        <button type="button" style={{ ...s.botao, padding: "6px 10px" }} onClick={salvar} disabled={ocupado}>
          {ocupado ? "…" : "✓"}
        </button>
        <button type="button" style={{ ...s.botaoSec, padding: "5px 9px" }} onClick={() => setEditando(false)}>
          ✕
        </button>
      </span>
      {erro && <span style={{ color: cores.vermelho, fontSize: "12px" }}>{erro}</span>}
      {precoOriginal && Number(precoOriginal) > Number(valor) && (
        <span style={{ ...s.suave, fontSize: "11px" }}>O preço riscado (de/por) será removido.</span>
      )}
    </span>
  );
}

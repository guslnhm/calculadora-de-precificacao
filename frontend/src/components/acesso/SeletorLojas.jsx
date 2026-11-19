import { useMemo, useState } from "react";
import { campo, cor } from "../painel/fmt";

// Escolha de lojas com busca (marca quantas quiser).

export default function SeletorLojas({ lojas, selecionadas, onMudar, altura = 220 }) {
  const [busca, setBusca] = useState("");
  const marcadas = useMemo(() => new Set(selecionadas), [selecionadas]);
  const nomes = useMemo(() => Object.fromEntries(lojas.map((l) => [l.chave, l.nome])), [lojas]);
  const filtradas = useMemo(() => {
    const b = busca.trim().toLowerCase();
    return b ? lojas.filter((l) => (l.nome || "").toLowerCase().includes(b)) : lojas;
  }, [lojas, busca]);

  function alternar(chave) {
    onMudar(marcadas.has(chave) ? selecionadas.filter((c) => c !== chave) : [...selecionadas, chave]);
  }

  return (
    <div style={{ display: "grid", gap: 8 }}>
      {selecionadas.length > 0 && (
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {selecionadas.map((c) => (
            <button key={c} type="button" onClick={() => alternar(c)} title="Tirar"
                    style={{ background: `${cor.azul}22`, border: `1px solid ${cor.azul}66`, color: cor.texto,
                      borderRadius: 999, padding: "4px 10px", fontSize: 12, cursor: "pointer" }}>
              {nomes[c] || c} ✕
            </button>
          ))}
        </div>
      )}
      <input style={campo} placeholder="Buscar loja..." value={busca} onChange={(e) => setBusca(e.target.value)} />
      <div style={{ maxHeight: altura, overflowY: "auto", border: `1px solid ${cor.borda}`, borderRadius: 8 }}>
        {filtradas.length === 0 && <div style={{ padding: 10, color: cor.textoSuave, fontSize: 13 }}>Nenhuma loja encontrada</div>}
        {filtradas.map((l) => (
          <label key={l.chave} style={{ display: "flex", alignItems: "center", gap: 8, padding: "7px 10px", cursor: "pointer",
            fontSize: 13, borderBottom: `1px solid ${cor.borda}`, background: marcadas.has(l.chave) ? `${cor.azul}14` : "transparent" }}>
            <input type="checkbox" checked={marcadas.has(l.chave)} onChange={() => alternar(l.chave)} />
            <span style={{ flex: 1 }}>{l.nome}</span>
            {l.plataforma && l.plataforma !== "IFOOD" && <span style={{ fontSize: 11, color: cor.textoSuave }}>{l.plataforma}</span>}
          </label>
        ))}
      </div>
    </div>
  );
}

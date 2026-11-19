import logo from "../../assets/rizq_logo_azul.png";
import { cor } from "../painel/fmt";

// Moldura das telas abertas sem login (convite e nova senha): logo, cartão central e campos.

export default function TelaPublica({ titulo, subtitulo, children }) {
  return (
    <div style={{ minHeight: "100vh", background: cor.fundo, color: cor.texto, 
      display: "grid", justifyItems: "center", alignContent: "start", padding: "40px 16px", boxSizing: "border-box" }}>
      <img src={logo} alt="RIZQ" style={{ width: 120, height: "auto", marginBottom: 24 }} />
      <div style={{ width: "100%", maxWidth: 440, background: cor.card, border: `1px solid ${cor.borda}`,
        borderRadius: 16, padding: 24, boxSizing: "border-box", display: "grid", gap: 16,
        boxShadow: "0 20px 60px rgba(64, 44, 28, 0.12)" }}>
        <div>
          <h1 style={{ margin: 0, fontSize: 24 }}>{titulo}</h1>
          {subtitulo && <div style={{ marginTop: 8, color: cor.textoSuave, fontSize: 14, lineHeight: 1.5 }}>{subtitulo}</div>}
        </div>
        {children}
      </div>
    </div>
  );
}

export function Campo({ rotulo, dica, ...props }) {
  return (
    <label style={{ display: "grid", gap: 6 }}>
      <span style={{ fontSize: 13, color: cor.textoSuave }}>{rotulo}</span>
      <input {...props} style={{ background: cor.campo, border: `1px solid ${cor.bordaCampo}`, color: cor.texto,
        borderRadius: 10, padding: "12px 14px", fontSize: 16, outline: "none", width: "100%", boxSizing: "border-box",
        colorScheme: "dark" }} />
      {dica && <span style={{ fontSize: 12, color: cor.textoSuave }}>{dica}</span>}
    </label>
  );
}


export function Aviso({ children, tipo = "erro" }) {
  const c = tipo === "erro" ? cor.ruim : cor.textoSuave;
  return <div style={{ color: c, fontSize: 14, lineHeight: 1.5, fontWeight: tipo === "erro" ? 600 : 400 }}>{children}</div>;
}

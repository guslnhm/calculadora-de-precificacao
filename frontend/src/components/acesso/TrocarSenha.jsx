import { useState } from "react";
import { authApi } from "../../services/api";
import { Aviso, Campo, botaoGrande } from "./TelaPublica";
import { cor } from "../painel/fmt";

// Janela para trocar a senha. Obrigatória quando a senha antiga era fraca.

export default function TrocarSenha({ obrigatoria = false, onFechar }) {
  const [atual, setAtual] = useState("");
  const [nova, setNova] = useState("");
  const [confirmar, setConfirmar] = useState("");
  const [erro, setErro] = useState("");
  const [ok, setOk] = useState(false);
  const [enviando, setEnviando] = useState(false);

  async function enviar(e) {
    e.preventDefault();
    setErro("");
    if (nova.length < 8) return setErro("A senha nova precisa ter pelo menos 8 caracteres");
    if (nova !== confirmar) return setErro("As senhas novas não são iguais");
    setEnviando(true);
    try {
      await authApi.trocarSenha(atual, nova);
      setOk(true);
    } catch (e2) {
      setErro(e2.message);
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div style={{ position: "fixed", inset: 0, background: "rgba(0,0,0,0.7)", zIndex: 50, display: "grid",
      placeItems: "center", padding: 16, overflowY: "auto" }}>
      <div style={{ width: "100%", maxWidth: 420, background: cor.card, border: `1px solid ${cor.borda}`,
        borderRadius: 16, padding: 24, boxSizing: "border-box", display: "grid", gap: 14, color: cor.texto }}>
        <h2 style={{ margin: 0, fontSize: 22 }}>{obrigatoria ? "Crie uma senha nova" : "Trocar senha"}</h2>
        {obrigatoria && !ok && (
          <Aviso tipo="info">A sua senha atual é curta demais. Para proteger os dados das lojas, crie uma com pelo menos 8 caracteres.</Aviso>
        )}
        {ok ? (
          <>
            <Aviso tipo="info">Senha trocada. Nos outros aparelhos, entre de novo com a senha nova.</Aviso>
            <button style={botaoGrande} onClick={() => onFechar(true)}>Fechar</button>
          </>
        ) : (
          <form onSubmit={enviar} style={{ display: "grid", gap: 12 }}>
            <Campo rotulo="Senha atual" type="password" value={atual} onChange={(e) => setAtual(e.target.value)}
                   autoComplete="current-password" required />
            <Campo rotulo="Senha nova" type="password" value={nova} onChange={(e) => setNova(e.target.value)}
                   autoComplete="new-password" dica="Pelo menos 8 caracteres" required />
            <Campo rotulo="Repita a senha nova" type="password" value={confirmar}
                   onChange={(e) => setConfirmar(e.target.value)} autoComplete="new-password" required />
            {erro && <Aviso>{erro}</Aviso>}
            <button style={botaoGrande} disabled={enviando}>{enviando ? "Salvando..." : "Salvar senha"}</button>
            {!obrigatoria && (
              <button type="button" onClick={() => onFechar(false)}
                      style={{ background: "none", border: "none", color: cor.textoSuave, cursor: "pointer", fontSize: 14 }}>
                Cancelar
              </button>
            )}
          </form>
        )}
      </div>
    </div>
  );
}

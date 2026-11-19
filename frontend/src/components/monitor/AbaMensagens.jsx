import { useEffect, useMemo, useState } from "react";
import { monitorApi } from "../../services/api";
import { cores, dataHora, pilula, s } from "./ui";

const SITUACOES = {
  ENVIADO: { rotulo: "Enviada", cor: cores.verde },
  SOMBRA: { rotulo: "Modo sombra (não enviada)", cor: cores.amarelo },
  FALHOU: { rotulo: "Falhou", cor: cores.vermelho },
};

const TIPOS = {
  alerta: "Alerta",
  monitor: "Aviso do monitor",
  itens: "Itens pausados",
  resumo: "Resumo diário",
  teste: "Teste",
};

/** Mostra *negrito* do WhatsApp como negrito. */
function TextoWhatsapp({ texto }) {
  const partes = String(texto || "").split(/(\*[^*\n]+\*)/g);
  return (
    <div style={{ whiteSpace: "pre-wrap", lineHeight: 1.5, fontSize: "14px", wordBreak: "break-word" }}>
      {partes.map((p, i) =>
        p.startsWith("*") && p.endsWith("*") && p.length > 2 ? <strong key={i}>{p.slice(1, -1)}</strong> : p
      )}
    </div>
  );
}

export default function AbaMensagens({ versao }) {
  const [linhas, setLinhas] = useState(null);
  const [erro, setErro] = useState("");
  const [grupo, setGrupo] = useState("");
  const [reenvio, setReenvio] = useState(""); // "" | "confirmar" | "enviando" | mensagem final

  useEffect(() => {
    monitorApi
      .mensagens(200)
      .then((d) => (setLinhas(d), setErro("")))
      .catch((e) => setErro(e.message));
  }, [versao]);

  const grupos = useMemo(() => [...new Set((linhas || []).map((l) => l.grupo).filter(Boolean))].sort(), [linhas]);
  // marcações internas (sem grupo, texto entre parênteses) não aparecem
  const visiveis = (linhas || [])
    .filter((l) => l.grupo || !String(l.corpo || "").startsWith("("))
    .filter((l) => !grupo || l.grupo === grupo);

  async function reenviarResumo() {
    setReenvio("enviando");
    try {
      const r = await monitorApi.enviarResumo();
      setReenvio(`Resumo de ontem reenviado para ${Object.keys(r || {}).length} grupo(s).`);
    } catch (e) {
      setReenvio(`Não consegui reenviar: ${e.message}`);
    }
  }

  return (
    <div>
      <p style={{ ...s.suave, marginTop: 0 }}>
        Tudo o que o monitor mandou (ou mandaria, no modo sombra) para os grupos, da mais recente para a mais antiga.
      </p>
      {erro && <div style={s.erro}>Não consegui carregar as mensagens: {erro}</div>}

      <div style={{ ...s.card, padding: "12px 16px", marginBottom: "12px", display: "flex", gap: "10px", alignItems: "center", flexWrap: "wrap" }}>
        <span style={s.suave}>📊 Resumo iFood do dia anterior saiu errado (ex.: o iFood atrasou os números)?</span>
        {reenvio === "" && (
          <button style={s.botaoSec} onClick={() => setReenvio("confirmar")}>Reenviar resumo de ontem</button>
        )}
        {reenvio === "confirmar" && (
          <>
            <span>Vai para todos os grupos. Confirmar?</span>
            <button style={s.botao} onClick={reenviarResumo}>Sim, reenviar</button>
            <button style={s.botaoSec} onClick={() => setReenvio("")}>Cancelar</button>
          </>
        )}
        {reenvio === "enviando" && <span style={s.suave}>Enviando…</span>}
        {!["", "confirmar", "enviando"].includes(reenvio) && (
          <>
            <span>{reenvio}</span>
            <button style={s.botaoSec} onClick={() => setReenvio("")}>OK</button>
          </>
        )}
      </div>

      {grupos.length > 1 && (
        <div style={{ marginBottom: "12px", maxWidth: "320px" }}>
          <select style={s.input} value={grupo} onChange={(e) => setGrupo(e.target.value)}>
            <option value="">Todos os grupos</option>
            {grupos.map((g) => (
              <option key={g} value={g}>
                {g}
              </option>
            ))}
          </select>
        </div>
      )}

      {linhas === null && !erro && <div style={s.vazio}>Carregando…</div>}
      {linhas !== null && visiveis.length === 0 && (
        <div style={{ ...s.card, ...s.vazio }}>
          Nenhuma mensagem ainda. Elas aparecem aqui quando alguma loja fecha, pausa ou volta.
        </div>
      )}

      <div style={{ display: "grid", gap: "10px" }}>
        {visiveis.map((m) => {
          const sit = SITUACOES[m.situacao] || { rotulo: m.situacao, cor: cores.cinza };
          return (
            <div key={m.id} style={{ ...s.card, padding: "14px 16px" }}>
              <div
                style={{
                  display: "flex",
                  gap: "8px",
                  alignItems: "center",
                  flexWrap: "wrap",
                  marginBottom: "8px",
                }}
              >
                <strong>{m.grupo || "—"}</strong>
                <span style={s.suave}>
                  {TIPOS[m.tipo] || m.tipo} · {dataHora(m.enviado_em)}
                </span>
                <span style={{ ...pilula(sit.cor), marginLeft: "auto" }}>{sit.rotulo}</span>
              </div>
              <TextoWhatsapp texto={m.corpo} />
              {m.erro && <div style={{ ...s.erro, margin: "10px 0 0" }}>{m.erro}</div>}
            </div>
          );
        })}
      </div>
    </div>
  );
}

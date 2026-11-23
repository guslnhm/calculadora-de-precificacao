import { useState } from "react";
import { whatsappApi } from "../../services/api";
import { cores, pilula, s } from "./ui";
import ConexaoWhatsapp from "./ConexaoWhatsapp";

/**
 * Números que enviam as mensagens. O principal vem do servidor (EVOLUTION_INSTANCIA); os outros são
 * cadastrados aqui, conectados por QR Code e escolhidos grupo a grupo (ou todos de uma vez).
 */
export default function NumerosWhatsapp({ dados, onMudou }) {
  const [novo, setNovo] = useState(null); // nome sendo digitado
  const [erro, setErro] = useState("");
  const [confirmar, setConfirmar] = useState(null); // { tipo: "mover"|"remover", n }
  const [ocupado, setOcupado] = useState(false);
  const [aviso, setAviso] = useState("");

  if (!dados) return null;
  if (!dados.variosNumeros) return <ConexaoWhatsapp />;

  async function executar(fn, msgOk) {
    setOcupado(true);
    setErro("");
    setAviso("");
    try {
      const r = await fn();
      setConfirmar(null);
      if (msgOk) setAviso(typeof msgOk === "function" ? msgOk(r) : msgOk);
      onMudou();
    } catch (e) {
      setErro(e.message);
    } finally {
      setOcupado(false);
    }
  }

  const numeros = dados.numeros || [];

  return (
    <div style={{ ...s.card, marginBottom: "16px", display: "grid", gap: "12px" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "8px", flexWrap: "wrap" }}>
        <h3 style={{ ...s.cardTitulo, margin: 0 }}>Números que enviam ({numeros.length})</h3>
        {novo === null && (
          <button style={s.botaoSec} onClick={() => setNovo("")}>
            + Adicionar número
          </button>
        )}
      </div>

      {novo !== null && (
        <form
          onSubmit={(e) => {
            e.preventDefault();
            executar(() => whatsappApi.criarNumero(novo), "Número adicionado. Clique em “Conectar número” nele e leia o QR Code com o celular.");
            setNovo(null);
          }}
          style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}
        >
          <input
            style={{ ...s.input, flex: "1 1 220px" }}
            placeholder="Nome do número (ex.: Número RIZQ)"
            value={novo}
            onChange={(e) => setNovo(e.target.value)}
            autoFocus
          />
          <button type="submit" style={s.botao} disabled={!novo.trim() || ocupado}>
            Adicionar
          </button>
          <button type="button" style={s.botaoSec} onClick={() => setNovo(null)}>
            Cancelar
          </button>
        </form>
      )}

      {numeros.map((n) => {
        const chave = n.instancia || "principal";
        const confirmando = confirmar && (confirmar.n.instancia || "principal") === chave ? confirmar.tipo : null;
        return (
          <div key={chave} style={{ borderTop: `1px solid ${cores.borda}`, paddingTop: "12px", display: "grid", gap: "8px" }}>
            <div style={{ display: "flex", gap: "8px", alignItems: "center", flexWrap: "wrap" }}>
              <strong>{n.nome}</strong>
              {n.principal && <span style={pilula(cores.azul)}>Principal</span>}
              <span style={s.suave}>
                {n.grupos} grupo(s) enviando por ele
              </span>
            </div>
            <ConexaoWhatsapp instancia={n.instancia} nome={n.nome} compacto />
            <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
              {confirmando === "mover" ? (
                <>
                  <span style={{ ...s.suave, alignSelf: "center" }}>Todos os grupos passam a receber por este número.</span>
                  <button style={s.botao} disabled={ocupado}
                          onClick={() => executar(() => whatsappApi.moverTodos(n.instancia), (r) => `${r.grupos} grupo(s) passaram para "${n.nome}".`)}>
                    Confirmar
                  </button>
                  <button style={s.botaoSec} onClick={() => setConfirmar(null)}>Não</button>
                </>
              ) : confirmando === "remover" ? (
                <>
                  <span style={{ ...s.suave, alignSelf: "center" }}>Os grupos dele voltam para o número principal.</span>
                  <button style={s.botaoPerigo} disabled={ocupado}
                          onClick={() => executar(() => whatsappApi.removerNumero(n.instancia), `"${n.nome}" removido.`)}>
                    Confirmar remoção
                  </button>
                  <button style={s.botaoSec} onClick={() => setConfirmar(null)}>Não</button>
                </>
              ) : (
                <>
                  <button style={s.botaoSec} onClick={() => setConfirmar({ tipo: "mover", n })}>
                    Usar este número em todos os grupos
                  </button>
                  {!n.principal && (
                    <button style={s.botaoPerigo} onClick={() => setConfirmar({ tipo: "remover", n })}>
                      Remover
                    </button>
                  )}
                </>
              )}
            </div>
          </div>
        );
      })}

      <div style={s.suave}>
        O número precisa estar <strong>dentro de cada grupo</strong> do WhatsApp que vai receber por ele. Escolha o número de
        cada grupo em &ldquo;Editar&rdquo;.
      </div>
      {aviso && <div style={{ ...s.ok, margin: 0 }}>{aviso}</div>}
      {erro && <div style={{ ...s.erro, margin: 0 }}>{erro}</div>}
    </div>
  );
}

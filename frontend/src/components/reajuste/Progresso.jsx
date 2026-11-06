import { useEffect, useState } from "react";
import { reajusteApi } from "../../services/api";
import { cores, dataHora, pilula, s } from "../monitor/ui";
import { reais } from "../cardapio/Controles";

export const SITUACOES_LOTE = {
  PUBLICANDO: { rotulo: "Publicando…", cor: cores.azul },
  CONCLUIDO: { rotulo: "Concluído", cor: cores.verde },
  DESFAZENDO: { rotulo: "Desfazendo…", cor: cores.amarelo },
  DESFEITO: { rotulo: "Desfeito", cor: cores.cinza },
};

/** Acompanha um lote (publicação ou desfazer) até terminar; mostra o resultado e o botão de desfazer. */
export default function Progresso({ loteId, onFechar }) {
  const [dados, setDados] = useState(null);
  const [erro, setErro] = useState("");
  const [confirmar, setConfirmar] = useState(false);
  const [versao, setVersao] = useState(0);

  useEffect(() => {
    let parar = false;
    let timer;
    async function ler() {
      try {
        const d = await reajusteApi.lote(loteId);
        if (parar) return;
        setDados(d);
        if (["PUBLICANDO", "DESFAZENDO"].includes(d.lote.situacao)) {
          timer = setTimeout(ler, 1500);
        }
      } catch (e) {
        if (!parar) setErro(e.message);
      }
    }
    ler();
    return () => {
      parar = true;
      clearTimeout(timer);
    };
  }, [loteId, versao]);

  async function desfazer() {
    try {
      await reajusteApi.desfazerLote(loteId);
      setConfirmar(false);
      setVersao((v) => v + 1);
    } catch (e) {
      setErro(e.message);
    }
  }

  if (!dados) return <div style={s.vazio}>{erro || "Carregando…"}</div>;
  const l = dados.lote;
  const sit = SITUACOES_LOTE[l.situacao] || { rotulo: l.situacao, cor: cores.cinza };
  const andamento = l.situacao === "DESFAZENDO" ? l.desfeitas : l.feitas + l.falhas;
  const pctFeito = l.total ? Math.min(100, Math.round((andamento / l.total) * 100)) : 0;
  const rodando = ["PUBLICANDO", "DESFAZENDO"].includes(l.situacao);
  const falhas = dados.alteracoes.filter((a) => a.situacao === "FALHOU");

  return (
    <div style={{ ...s.card, display: "grid", gap: "14px" }}>
      <div style={{ display: "flex", gap: "10px", alignItems: "center", flexWrap: "wrap" }}>
        <h3 style={{ margin: 0 }}>Reajuste #{l.id}</h3>
        <span style={pilula(sit.cor)}>{sit.rotulo}</span>
        <span style={s.suave}>
          {dataHora(l.criadoEm)}
          {l.usuario ? ` · por ${l.usuario}` : ""}
        </span>
      </div>
      <div>
        <div style={{ height: "10px", background: cores.campo, borderRadius: "6px", overflow: "hidden" }}>
          <div
            style={{
              height: "100%",
              width: `${rodando ? pctFeito : 100}%`,
              background: l.situacao === "DESFEITO" ? cores.cinza : l.falhas ? cores.amarelo : cores.verde,
              transition: "width 0.4s",
            }}
          />
        </div>
        <div style={{ ...s.suave, marginTop: "6px" }}>
          {l.feitas} de {l.total} preço(s) publicado(s)
          {l.falhas ? ` · ${l.falhas} falha(s)` : ""}
          {l.desfeitas ? ` · ${l.desfeitas} desfeito(s)` : ""}
          {rodando && " · pode sair desta tela, continua rodando no servidor"}
        </div>
      </div>
      {erro && <div style={s.erro}>{erro}</div>}
      {falhas.length > 0 && (
        <div style={s.aviso}>
          <strong>O iFood recusou {falhas.length}:</strong>
          <ul style={{ margin: "6px 0 0", paddingLeft: "18px" }}>
            {falhas.slice(0, 10).map((a) => (
              <li key={a.id}>
                {a.loja} · {a.nome} ({reais(a.depois)}): {a.erro}
              </li>
            ))}
          </ul>
        </div>
      )}
      <div style={{ display: "flex", gap: "8px", justifyContent: "flex-end", flexWrap: "wrap" }}>
        {l.situacao === "CONCLUIDO" && l.feitas > 0 && !confirmar && (
          <button style={s.botaoPerigo} onClick={() => setConfirmar(true)}>
            Desfazer este reajuste
          </button>
        )}
        {confirmar && (
          <>
            <span style={{ color: cores.amarelo, alignSelf: "center", fontSize: "14px" }}>
              Volta {l.feitas} preço(s) para o valor de antes. Confirma?
            </span>
            <button style={s.botaoPerigo} onClick={desfazer}>
              Sim, desfazer
            </button>
            <button style={s.botaoSec} onClick={() => setConfirmar(false)}>
              Não
            </button>
          </>
        )}
        {onFechar && !rodando && (
          <button style={s.botaoSec} onClick={onFechar}>
            Fechar
          </button>
        )}
      </div>
    </div>
  );
}

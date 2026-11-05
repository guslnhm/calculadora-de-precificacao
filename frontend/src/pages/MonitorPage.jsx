import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { monitorApi } from "../services/api";
import { cores, pilula, s } from "../components/monitor/ui";
import AbaLojas from "../components/monitor/AbaLojas";
import AbaIncidentes from "../components/monitor/AbaIncidentes";
import AbaMensagens from "../components/monitor/AbaMensagens";
import AbaGrupos from "../components/monitor/AbaGrupos";

const ABAS = [
  { id: "lojas", rotulo: "Lojas agora" },
  { id: "incidentes", rotulo: "Incidentes" },
  { id: "mensagens", rotulo: "Mensagens" },
  { id: "grupos", rotulo: "Grupos do WhatsApp" },
];

function SituacaoMonitor({ cfg }) {
  if (!cfg) return null;
  if (!cfg.ativo) {
    return (
      <span style={pilula(cores.cinza)} title="MONITOR_ATIVO=false no Railway">
        ● Monitor desligado
      </span>
    );
  }
  if (!cfg.enviarWhatsapp) {
    return (
      <span
        style={pilula(cores.amarelo)}
        title="MONITOR_ENVIAR_WHATSAPP=false: as mensagens são registradas, mas não vão para os grupos"
      >
        ● Modo sombra: não envia no WhatsApp
      </span>
    );
  }
  return <span style={pilula(cores.verde)}>● Monitorando e enviando alertas</span>;
}

export default function MonitorPage() {
  const [params, setParams] = useSearchParams();
  const aba = ABAS.some((a) => a.id === params.get("aba")) ? params.get("aba") : "lojas";
  const [cfg, setCfg] = useState(null);
  const [rodando, setRodando] = useState(false);
  const [aviso, setAviso] = useState("");
  const [versao, setVersao] = useState(0);

  useEffect(() => {
    monitorApi.configuracao().then(setCfg).catch(() => setCfg(null));
  }, []);

  async function rodarAgora() {
    setRodando(true);
    setAviso("");
    try {
      const r = await monitorApi.rodarCiclo();
      setAviso(
        `Leitura feita: ${r.lojas} loja(s), ${r.eventos} novidade(s)` +
          (r.eventos ? " (veja a aba Mensagens)." : ".")
      );
      setVersao((v) => v + 1);
    } catch (e) {
      setAviso(`Erro: ${e.message}`);
    } finally {
      setRodando(false);
    }
  }

  return (
    <div style={s.page}>
      <div style={s.header}>
        <div>
          <h2 style={s.titulo}>Monitor iFood</h2>
          <p style={s.subtitulo}>
            Status das lojas a cada {cfg?.intervaloSegundos ?? 30}s, incidentes e alertas nos grupos.
          </p>
        </div>
        <div style={{ display: "flex", gap: "10px", alignItems: "center", flexWrap: "wrap" }}>
          <SituacaoMonitor cfg={cfg} />
          <button style={s.botaoSec} onClick={rodarAgora} disabled={rodando}>
            {rodando ? "Lendo…" : "Ler agora"}
          </button>
        </div>
      </div>

      {aviso && <div style={aviso.startsWith("Erro") ? s.erro : s.ok}>{aviso}</div>}

      <div style={s.abas} role="tablist">
        {ABAS.map((a) => (
          <button
            key={a.id}
            role="tab"
            aria-selected={aba === a.id}
            style={{ ...s.aba, ...(aba === a.id ? s.abaAtiva : {}) }}
            onClick={() => setParams({ aba: a.id })}
          >
            {a.rotulo}
          </button>
        ))}
      </div>

      {aba === "lojas" && <AbaLojas versao={versao} />}
      {aba === "incidentes" && <AbaIncidentes versao={versao} />}
      {aba === "mensagens" && <AbaMensagens versao={versao} />}
      {aba === "grupos" && <AbaGrupos />}
    </div>
  );
}

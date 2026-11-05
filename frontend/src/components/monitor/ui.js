import { useEffect, useState } from "react";

// Estilos e funções de apoio das telas do monitor iFood (mesmo tema escuro do resto do site).

export const FUSO = "America/Sao_Paulo";

export const cores = {
  fundo: "#0D1117",
  card: "#161B22",
  borda: "#26313B",
  bordaCampo: "#30363D",
  campo: "#21262D",
  texto: "#F6F8FA",
  textoSuave: "#9BA7B4",
  destaque: "#E63946",
  vermelho: "#F85149",
  amarelo: "#D29922",
  azul: "#58A6FF",
  verde: "#3FB950",
  cinza: "#8B949E",
};

export const ESTADOS = {
  ERROR: { rotulo: "Fechada", cor: cores.vermelho, ordem: 0 },
  PAUSED: { rotulo: "Pausada", cor: cores.amarelo, ordem: 1 },
  WARNING: { rotulo: "Com restrição", cor: cores.azul, ordem: 2 },
  CLOSED: { rotulo: "Fora do horário", cor: cores.cinza, ordem: 3 },
  OK: { rotulo: "Aberta", cor: cores.verde, ordem: 4 },
  AUSENTE: { rotulo: "Sumiu da API", cor: cores.cinza, ordem: 5 },
};

export function estadoInfo(estado) {
  return ESTADOS[estado] || { rotulo: estado || "—", cor: cores.cinza, ordem: 9 };
}

const MOTIVOS_CURTOS = {
  PAUSA: "pausa",
  DESCONECTADA: "PDV/gestor desconectado",
  FORA_DO_HORARIO: "fora do horário",
  RAIO_REDUZIDO: "raio reduzido",
  FINANCEIRO: "pendência financeira",
  LOGISTICA: "bloqueio logístico",
  PENALIDADE: "penalidade do iFood",
  LOJA_INDISPONIVEL: "loja desativada/em teste",
  OUTRO: "outro motivo",
};

/** O backend devolve os motivos como JSON em texto (painel) ou como lista (incidentes). */
export function lerMotivos(motivos) {
  if (!motivos) return [];
  if (Array.isArray(motivos)) return motivos;
  try {
    const lista = JSON.parse(motivos);
    return Array.isArray(lista) ? lista : [];
  } catch {
    return [];
  }
}

export function textoMotivos(motivos, estado) {
  const lista = lerMotivos(motivos).filter(
    (m) => !(m.tipo === "FORA_DO_HORARIO" && estado !== "CLOSED")
  );
  const vistos = [];
  lista.forEach((m) => {
    const t = MOTIVOS_CURTOS[m.tipo] || m.codigo || "outro motivo";
    if (!vistos.includes(t)) vistos.push(t);
  });
  return vistos.join(" + ");
}

export function detalheMotivos(motivos) {
  return lerMotivos(motivos)
    .map((m) => `${m.codigo}${m.texto ? ` — ${m.texto}` : ""}`)
    .join("\n");
}

export function paraData(v) {
  if (v === null || v === undefined || v === "") return null;
  const d = new Date(v);
  return Number.isNaN(d.getTime()) ? null : d;
}

export function hora(v) {
  const d = paraData(v);
  return d ? d.toLocaleTimeString("pt-BR", { timeZone: FUSO, hour: "2-digit", minute: "2-digit" }) : "—";
}

export function dataHora(v) {
  const d = paraData(v);
  if (!d) return "—";
  return d.toLocaleString("pt-BR", {
    timeZone: FUSO,
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/** "45 min", "2h05", "1d 3h00" (mesmo formato das mensagens do WhatsApp). */
export function duracaoMin(minutos) {
  const min = Math.max(0, Math.floor(minutos || 0));
  if (min < 60) return `${min} min`;
  const h = Math.floor(min / 60);
  const m = String(min % 60).padStart(2, "0");
  if (h < 24) return `${h}h${m}`;
  return `${Math.floor(h / 24)}d ${h % 24}h${m}`;
}

export function desdeAgora(v, agora = Date.now()) {
  const d = paraData(v);
  return d ? duracaoMin((agora - d.getTime()) / 60000) : "—";
}

/** yyyy-MM-dd no fuso de São Paulo. */
export function diaISO(data = new Date()) {
  return data.toLocaleDateString("en-CA", { timeZone: FUSO });
}

export function somarDias(iso, dias) {
  const [a, m, d] = iso.split("-").map(Number);
  const dt = new Date(Date.UTC(a, m - 1, d + dias));
  return dt.toISOString().slice(0, 10);
}

export const s = {
  page: { color: cores.texto },
  header: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "flex-start",
    gap: "16px",
    flexWrap: "wrap",
    marginBottom: "18px",
  },
  titulo: { margin: "0 0 6px" },
  subtitulo: { margin: 0, color: cores.textoSuave, fontSize: "14px" },
  card: {
    backgroundColor: cores.card,
    border: `1px solid ${cores.borda}`,
    borderRadius: "18px",
    padding: "20px",
  },
  cardTitulo: { margin: "0 0 14px", fontSize: "18px" },
  abas: {
    display: "flex",
    gap: "6px",
    flexWrap: "wrap",
    borderBottom: `1px solid ${cores.borda}`,
    marginBottom: "18px",
  },
  aba: {
    background: "transparent",
    border: "none",
    borderBottom: "2px solid transparent",
    color: cores.textoSuave,
    padding: "10px 14px",
    cursor: "pointer",
    fontSize: "15px",
  },
  abaAtiva: { color: cores.texto, borderBottom: `2px solid ${cores.destaque}`, fontWeight: "bold" },
  botao: {
    backgroundColor: cores.destaque,
    color: cores.texto,
    border: "none",
    padding: "10px 16px",
    borderRadius: "10px",
    fontWeight: "bold",
    cursor: "pointer",
  },
  botaoSec: {
    backgroundColor: "transparent",
    color: cores.texto,
    border: `1px solid ${cores.bordaCampo}`,
    padding: "9px 14px",
    borderRadius: "10px",
    cursor: "pointer",
  },
  botaoPerigo: {
    backgroundColor: "transparent",
    color: cores.vermelho,
    border: `1px solid ${cores.vermelho}`,
    padding: "9px 14px",
    borderRadius: "10px",
    cursor: "pointer",
  },
  input: {
    backgroundColor: cores.campo,
    border: `1px solid ${cores.bordaCampo}`,
    padding: "10px 12px",
    borderRadius: "10px",
    color: cores.texto,
    outline: "none",
    fontSize: "14px",
    width: "100%",
  },
  label: { color: cores.textoSuave, fontSize: "13px", display: "grid", gap: "6px" },
  check: { display: "flex", alignItems: "center", gap: "8px", cursor: "pointer", fontSize: "14px" },
  tabelaWrap: { overflowX: "auto" },
  tabela: { width: "100%", borderCollapse: "collapse", fontSize: "14px", border: "none" },
  th: {
    textAlign: "left",
    color: cores.textoSuave,
    fontWeight: "normal",
    fontSize: "12px",
    textTransform: "uppercase",
    letterSpacing: "0.04em",
    padding: "8px 10px",
    border: "none",
    borderBottom: `1px solid ${cores.borda}`,
    whiteSpace: "nowrap",
  },
  td: { padding: "10px", border: "none", borderBottom: `1px solid ${cores.borda}`, verticalAlign: "top" },
  erro: {
    backgroundColor: "rgba(248,81,73,0.1)",
    border: `1px solid ${cores.vermelho}`,
    color: "#FFA198",
    borderRadius: "10px",
    padding: "10px 14px",
    marginBottom: "14px",
    fontSize: "14px",
  },
  aviso: {
    backgroundColor: "rgba(210,153,34,0.1)",
    border: `1px solid ${cores.amarelo}`,
    color: "#E3B341",
    borderRadius: "10px",
    padding: "10px 14px",
    marginBottom: "14px",
    fontSize: "14px",
  },
  ok: {
    backgroundColor: "rgba(63,185,80,0.1)",
    border: `1px solid ${cores.verde}`,
    color: "#7EE787",
    borderRadius: "10px",
    padding: "10px 14px",
    marginBottom: "14px",
    fontSize: "14px",
  },
  vazio: { color: cores.textoSuave, padding: "24px 0", textAlign: "center" },
  suave: { color: cores.textoSuave, fontSize: "13px" },
};

export function pilula(cor) {
  return {
    display: "inline-flex",
    alignItems: "center",
    gap: "6px",
    padding: "3px 10px",
    borderRadius: "999px",
    fontSize: "12px",
    fontWeight: "bold",
    whiteSpace: "nowrap",
    color: cor,
    backgroundColor: `${cor}1F`,
    border: `1px solid ${cor}66`,
  };
}

/** true quando a tela é estreita (celular). */
export function useTelaEstreita(limite = 640) {
  const [estreita, setEstreita] = useState(() => window.innerWidth < limite);
  useEffect(() => {
    const aoMudar = () => setEstreita(window.innerWidth < limite);
    window.addEventListener("resize", aoMudar);
    return () => window.removeEventListener("resize", aoMudar);
  }, [limite]);
  return estreita;
}

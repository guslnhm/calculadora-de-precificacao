import { cores } from "../monitor/ui";

// Formatação e cores do painel (mesmo tema escuro do site).

export const cor = {
  ...cores,
  serie1: "#3987e5", // azul (série principal)
  serie2: "#d95926", // laranja (comparação)
  bom: "#3FB950",
  ruim: "#F85149",
  grade: "#26313B",
};

const brl = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const brl0 = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL", maximumFractionDigits: 0 });
const num = new Intl.NumberFormat("pt-BR");

export const vazio = (v) => v === null || v === undefined || Number.isNaN(Number(v));

export function reais(v, inteiro = false) {
  if (vazio(v)) return "—";
  return (inteiro ? brl0 : brl).format(Number(v));
}

/** R$ 1,03 mi / R$ 68,7 mil — para cartões grandes. */
export function reaisCurto(v) {
  if (vazio(v)) return "—";
  const n = Number(v);
  if (Math.abs(n) >= 1e6) return `R$ ${(n / 1e6).toLocaleString("pt-BR", { maximumFractionDigits: 2 })} mi`;
  if (Math.abs(n) >= 1e4) return `R$ ${(n / 1e3).toLocaleString("pt-BR", { maximumFractionDigits: 1 })} mil`;
  return reais(n);
}

export function inteiro(v) {
  return vazio(v) ? "—" : num.format(Math.round(Number(v)));
}

/** 0,192 -> "19,2%" */
export function percentual(v, casas = 1) {
  if (vazio(v)) return "—";
  return `${(Number(v) * 100).toLocaleString("pt-BR", { minimumFractionDigits: casas, maximumFractionDigits: casas })}%`;
}

export function minutos(v) {
  if (vazio(v)) return "—";
  const m = Math.round(Number(v));
  if (m < 60) return `${m} min`;
  return `${Math.floor(m / 60)}h${String(m % 60).padStart(2, "0")}`;
}

/** "19/10" ou "19/10 a 25/10" ou "out/26". */
export function dataCurta(iso) {
  if (!iso) return "—";
  const [, m, d] = iso.split("-");
  return `${d}/${m}`;
}

const MESES = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"];

export function rotuloPeriodo(p, curto = false) {
  if (!p) return "—";
  if (p.tipo === "MENSAL") {
    const [a, m] = p.inicio.split("-");
    return curto ? `${MESES[Number(m) - 1]}/${a.slice(2)}` : `${MESES[Number(m) - 1]} de ${a}`;
  }
  return curto ? dataCurta(p.inicio) : `${dataCurta(p.inicio)} a ${dataCurta(p.fim)}`;
}

/** Soma dias/meses a uma data ISO. */
export function moverPeriodo(p, passos) {
  const d = new Date(`${p.inicio}T12:00:00`);
  if (p.tipo === "MENSAL") d.setMonth(d.getMonth() + passos);
  else d.setDate(d.getDate() + 7 * passos);
  return d.toISOString().slice(0, 10);
}

/** Variação: { texto: "▲ 7,8%", cor } — "inverso" para métricas em que subir é ruim (cancelamentos). */
export function variacao(v, { inverso = false, pontos = false } = {}) {
  if (vazio(v)) return { texto: "—", cor: cor.textoSuave };
  const n = Number(v);
  if (n === 0) return { texto: pontos ? "= 0 p.p." : "= 0%", cor: cor.textoSuave };
  const sobe = n > 0;
  const bom = inverso ? !sobe : sobe;
  const valor = Math.abs(n).toLocaleString("pt-BR", { maximumFractionDigits: 1 });
  return { texto: `${sobe ? "▲" : "▼"} ${valor}${pontos ? " p.p." : "%"}`, cor: bom ? cor.bom : cor.ruim };
}

export const FONTES = {
  ANALYTICS: { rotulo: "iFood API", dica: "Dados do Analytics do iFood (D-1)" },
  BI: { rotulo: "BI antigo", dica: "Histórico importado do Power BI" },
};

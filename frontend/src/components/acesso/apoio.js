import { botao, cor } from "../painel/fmt";

// Funções de apoio da tela de Acessos.

export const PERFIS = {
  MENTORADO: { rotulo: "Mentorado", cor: cor.azul },
  EQUIPE: { rotulo: "Equipe", cor: cor.verde },
  ADMIN: { rotulo: "Admin", cor: cor.verde },
};

export function perfilInfo(p) {
  return PERFIS[p] || { rotulo: p || "—", cor: cor.cinza };
}

export function linkConvite(token) {
  return `${window.location.origin}/convite/${token}`;
}

export function linkNovaSenha(token) {
  return `${window.location.origin}/nova-senha/${token}`;
}

/** "hoje 14:32", "ontem", "há 5 dias", "nunca entrou". */
export function quandoAcessou(iso, agora = new Date()) {
  if (!iso) return "nunca entrou";
  const d = new Date(iso);
  const fuso = "America/Sao_Paulo";
  const dia = (x) => x.toLocaleDateString("en-CA", { timeZone: fuso });
  const hora = d.toLocaleTimeString("pt-BR", { timeZone: fuso, hour: "2-digit", minute: "2-digit" });
  const dias = Math.round((Date.parse(dia(agora)) - Date.parse(dia(d))) / 86400000);
  if (dias <= 0) return `hoje ${hora}`;
  if (dias === 1) return `ontem ${hora}`;
  if (dias < 30) return `há ${dias} dias`;
  return d.toLocaleDateString("pt-BR", { timeZone: fuso });
}

/** Dias desde o último acesso (null = nunca). */
export function diasSemAcesso(iso, agora = new Date()) {
  if (!iso) return null;
  return Math.floor((agora.getTime() - new Date(iso).getTime()) / 86400000);
}

/** Cor do engajamento: verde (usou na semana), amarelo (no mês), vermelho (sumido ou nunca). */
export function corUso(u) {
  if (u.diasAtivos7 > 0) return cor.verde;
  if (u.diasAtivos30 > 0) return cor.amarelo;
  return cor.vermelho;
}

export function dataBr(iso) {
  if (!iso) return "—";
  const [a, m, d] = iso.split("-");
  return `${d}/${m}/${a}`;
}

export function aniversario(iso) {
  if (!iso) return "—";
  const [, m, d] = iso.split("-");
  return `${d}/${m}`;
}

export async function copiar(texto) {
  try {
    await navigator.clipboard.writeText(texto);
    return true;
  } catch {
    window.prompt("Copie o texto:", texto);
    return false;
  }
}

export const botaoSec = { ...botao, background: cor.campo, border: `1px solid ${cor.bordaCampo}`, fontWeight: 500 };

export const botaoGrande = {
  background: cor.destaque,
  border: "none",
  color: "#fff",
  borderRadius: 10,
  padding: "13px 16px",
  fontSize: 16,
  fontWeight: 700,
  cursor: "pointer",
};

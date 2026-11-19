import { useMemo } from "react";
import { caixa, cor, dataCurta } from "../painel/fmt";
import { GraficoBarras } from "../painel/Graficos";
import { aniversario, corUso, quandoAcessou } from "./apoio";

// Adesão dos mentorados: quem está usando, quem sumiu e quem faz aniversário.

function Numero({ titulo, valor, nota, corValor }) {
  return (
    <div style={{ ...caixa, display: "grid", gap: 4 }}>
      <div style={{ fontSize: 12, color: cor.textoSuave }}>{titulo}</div>
      <div style={{ fontSize: 28, fontWeight: 700, color: corValor || cor.texto }}>{valor}</div>
      {nota && <div style={{ fontSize: 12, color: cor.textoSuave }}>{nota}</div>}
    </div>
  );
}

export default function AbaAdesao({ adesao, usuarios }) {
  const mentorados = useMemo(() => (usuarios || [])
    .filter((u) => u.perfil === "MENTORADO" && u.ativo)
    .sort((a, b) => (b.ultimoAcesso || "").localeCompare(a.ultimoAcesso || "")), [usuarios]);

  const aniversariantes = useMemo(() => (usuarios || [])
    .filter((u) => u.ativo && u.diasParaAniversario != null && u.diasParaAniversario <= 30)
    .sort((a, b) => a.diasParaAniversario - b.diasParaAniversario), [usuarios]);

  if (!adesao) return null;
  const pct = (n) => (adesao.mentorados ? `${Math.round((n / adesao.mentorados) * 100)}% dos mentorados` : undefined);

  return (
    <div style={{ display: "grid", gap: 16, gridTemplateColumns: "minmax(0, 1fr)" }}>
      <div style={{ display: "grid", gap: 12, gridTemplateColumns: "repeat(auto-fit, minmax(min(160px, 100%), 1fr))" }}>
        <Numero titulo="Mentorados com acesso" valor={adesao.mentorados} />
        <Numero titulo="Usaram nos últimos 7 dias" valor={adesao.ativos7} nota={pct(adesao.ativos7)} corValor={cor.verde} />
        <Numero titulo="Usaram nos últimos 30 dias" valor={adesao.ativos30} nota={pct(adesao.ativos30)} />
        <Numero titulo="Nunca entraram" valor={adesao.nuncaEntraram} corValor={adesao.nuncaEntraram ? cor.amarelo : cor.texto}
                nota={adesao.nuncaEntraram ? "cadastraram, mas não voltaram" : undefined} />
      </div>

      <div style={caixa}>
        <div style={{ fontWeight: 700, marginBottom: 4 }}>Mentorados que usaram, por dia</div>
        <div style={{ fontSize: 12, color: cor.textoSuave, marginBottom: 8 }}>últimos 30 dias</div>
        <GraficoBarras formato={(n) => String(Math.round(n))} altura={170}
                       itens={adesao.dias.map((d) => ({ rotulo: dataCurta(d.dia), valor: d.mentorados,
                         dica: `${dataCurta(d.dia)} · ${d.mentorados} mentorado(s)` }))} />
      </div>

      {aniversariantes.length > 0 && (
        <div style={caixa}>
          <div style={{ fontWeight: 700, marginBottom: 8 }}>🎂 Aniversários nos próximos 30 dias</div>
          <div style={{ display: "grid", gap: 6 }}>
            {aniversariantes.map((u) => (
              <div key={u.id} style={{ display: "flex", gap: 10, alignItems: "baseline", flexWrap: "wrap", fontSize: 14 }}>
                <span style={{ minWidth: 48, fontWeight: 700, color: u.aniversarioHoje ? cor.destaque : cor.texto }}>
                  {aniversario(u.dataNascimento)}
                </span>
                <span style={{ fontWeight: 600 }}>{u.nome}</span>
                <span style={{ color: cor.textoSuave, fontSize: 12 }}>
                  {u.aniversarioHoje ? "é hoje!" : u.diasParaAniversario === 1 ? "amanhã" : `em ${u.diasParaAniversario} dias`}
                  {u.lojas?.length ? ` · ${u.lojas.map((l) => l.nome).join(", ")}` : ""}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

      <div style={{ ...caixa, padding: 0, overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13, minWidth: 620 }}>
          <thead>
            <tr style={{ color: cor.textoSuave, textAlign: "left" }}>
              <th style={th}>Mentorado</th>
              <th style={th}>Lojas</th>
              <th style={th}>Último acesso</th>
              <th style={{ ...th, textAlign: "right" }} title="Dias com acesso nos últimos 7 dias">7 dias</th>
              <th style={{ ...th, textAlign: "right" }} title="Dias com acesso nos últimos 30 dias">30 dias</th>
              <th style={{ ...th, textAlign: "right" }} title="Logins nos últimos 30 dias">Entradas</th>
            </tr>
          </thead>
          <tbody>
            {mentorados.length === 0 && (
              <tr><td colSpan={6} style={{ ...td, color: cor.textoSuave }}>Nenhum mentorado ainda. Crie um convite na aba Convites.</td></tr>
            )}
            {mentorados.map((u) => (
              <tr key={u.id} style={{ borderTop: `1px solid ${cor.borda}` }}>
                <td style={td}>
                  <span style={{ display: "inline-block", width: 8, height: 8, borderRadius: 4, background: corUso(u), marginRight: 8 }} />
                  <span style={{ fontWeight: 600 }}>{u.nome}</span>
                  <div style={{ fontSize: 11, color: cor.textoSuave, marginLeft: 16 }}>{u.email}</div>
                </td>
                <td style={{ ...td, color: cor.textoSuave }}>{u.lojas.map((l) => l.nome).join(", ") || "—"}</td>
                <td style={{ ...td, color: u.ultimoAcesso ? cor.texto : cor.amarelo }}>{quandoAcessou(u.ultimoAcesso)}</td>
                <td style={{ ...td, textAlign: "right" }}>{u.diasAtivos7}</td>
                <td style={{ ...td, textAlign: "right" }}>{u.diasAtivos30}</td>
                <td style={{ ...td, textAlign: "right" }}>{u.logins30}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div style={{ fontSize: 12, color: cor.textoSuave, display: "flex", gap: 14, flexWrap: "wrap" }}>
        {[[cor.verde, "usou nesta semana"], [cor.amarelo, "usou no mês"], [cor.vermelho, "sem uso há 30 dias ou nunca entrou"]].map(([c, t]) => (
          <span key={t}><span style={{ display: "inline-block", width: 8, height: 8, borderRadius: 4, background: c, marginRight: 6 }} />{t}</span>
        ))}
      </div>
    </div>
  );
}

const th = { padding: "10px 12px", fontWeight: 600, fontSize: 12 };
const td = { padding: "10px 12px", verticalAlign: "top" };

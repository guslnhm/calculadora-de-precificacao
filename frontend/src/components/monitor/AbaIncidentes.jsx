import { useEffect, useMemo, useState } from "react";
import { monitorApi } from "../../services/api";
import {
  cores,
  dataHora,
  detalheMotivos,
  diaISO,
  duracaoMin,
  estadoInfo,
  pilula,
  s,
  somarDias,
  textoMotivos,
} from "./ui";

const ATALHOS = [
  { rotulo: "Hoje", dias: 0 },
  { rotulo: "7 dias", dias: 6 },
  { rotulo: "30 dias", dias: 29 },
];

export default function AbaIncidentes({ versao }) {
  const hoje = diaISO();
  const [de, setDe] = useState(somarDias(hoje, -6));
  const [ate, setAte] = useState(hoje);
  const [resultado, setResultado] = useState({ chave: null, linhas: null, erro: "" });
  const [busca, setBusca] = useState("");
  const chave = `${de}|${ate}|${versao}`;
  const carregado = resultado.chave === chave;
  const linhas = carregado ? resultado.linhas : null;
  const erro = carregado ? resultado.erro : "";

  useEffect(() => {
    let cancelado = false;
    monitorApi
      .incidentes(de, ate)
      .then((d) => !cancelado && setResultado({ chave, linhas: d, erro: "" }))
      .catch((e) => !cancelado && setResultado({ chave, linhas: null, erro: e.message }));
    return () => {
      cancelado = true;
    };
  }, [de, ate, chave]);

  const visiveis = useMemo(() => {
    const termo = busca.trim().toLowerCase();
    return (linhas || []).filter((l) => !termo || String(l.loja).toLowerCase().includes(termo));
  }, [linhas, busca]);

  const porLoja = useMemo(() => {
    const m = new Map();
    visiveis.forEach((l) => {
      const atual = m.get(l.loja) || { loja: l.loja, minutos: 0, vezes: 0 };
      atual.minutos += l.minutos || 0;
      atual.vezes += 1;
      m.set(l.loja, atual);
    });
    return [...m.values()].sort((a, b) => b.minutos - a.minutos);
  }, [visiveis]);

  const totalMin = porLoja.reduce((t, l) => t + l.minutos, 0);
  const maior = porLoja[0]?.minutos || 1;

  return (
    <div>
      <div style={{ ...s.card, marginBottom: "16px" }}>
        <div style={{ display: "flex", gap: "12px", flexWrap: "wrap", alignItems: "flex-end" }}>
          <label style={s.label}>
            De
            <input type="date" style={s.input} value={de} max={ate} onChange={(e) => setDe(e.target.value)} />
          </label>
          <label style={s.label}>
            Até
            <input type="date" style={s.input} value={ate} min={de} onChange={(e) => setAte(e.target.value)} />
          </label>
          <div style={{ display: "flex", gap: "6px" }}>
            {ATALHOS.map((a) => (
              <button
                key={a.rotulo}
                style={s.botaoSec}
                onClick={() => {
                  setAte(hoje);
                  setDe(somarDias(hoje, -a.dias));
                }}
              >
                {a.rotulo}
              </button>
            ))}
          </div>
          <label style={{ ...s.label, flex: "1 1 200px" }}>
            Loja
            <input
              style={s.input}
              placeholder="Filtrar por nome…"
              value={busca}
              onChange={(e) => setBusca(e.target.value)}
            />
          </label>
        </div>
      </div>

      {erro && <div style={s.erro}>Não consegui carregar os incidentes: {erro}</div>}
      {linhas === null && !erro && <div style={s.vazio}>Carregando…</div>}

      {linhas !== null && (
        <div
          style={{
            display: "grid",
            gap: "16px",
          }}
        >
          <div style={s.card}>
            <h3 style={s.cardTitulo}>Tempo fechado por loja</h3>
            {porLoja.length === 0 ? (
              <div style={s.vazio}>Nenhum incidente no período. ✅</div>
            ) : (
              <>
                <div style={{ ...s.suave, marginBottom: "12px" }}>
                  {visiveis.length} incidente(s) em {porLoja.length} loja(s) · {duracaoMin(totalMin)} no total
                </div>
                <div style={{ display: "grid", gap: "10px" }}>
                  {porLoja.slice(0, 10).map((l) => (
                    <div key={l.loja}>
                      <div style={{ display: "flex", justifyContent: "space-between", gap: "8px", fontSize: "14px" }}>
                        <span>{l.loja}</span>
                        <span style={{ fontVariantNumeric: "tabular-nums", whiteSpace: "nowrap" }}>
                          {duracaoMin(l.minutos)} <span style={s.suave}>· {l.vezes}x</span>
                        </span>
                      </div>
                      <div style={{ height: "6px", background: cores.campo, borderRadius: "4px", marginTop: "4px" }}>
                        <div
                          style={{
                            height: "100%",
                            width: `${Math.max(2, (l.minutos / maior) * 100)}%`,
                            background: cores.destaque,
                            borderRadius: "4px",
                          }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              </>
            )}
          </div>

          <div style={{ ...s.card, minWidth: 0 }}>
            <h3 style={s.cardTitulo}>Incidentes</h3>
            {visiveis.length === 0 ? (
              <div style={s.vazio}>Nada para mostrar.</div>
            ) : (
              <div style={s.tabelaWrap}>
                <table style={s.tabela}>
                  <thead>
                    <tr>
                      <th style={s.th}>Loja</th>
                      <th style={s.th}>Status</th>
                      <th style={s.th}>Motivo</th>
                      <th style={s.th}>Início</th>
                      <th style={s.th}>Duração</th>
                    </tr>
                  </thead>
                  <tbody>
                    {visiveis.map((l) => {
                      const info = estadoInfo(l.estado);
                      return (
                        <tr key={l.id}>
                          <td style={{ ...s.td, fontWeight: "bold" }}>{l.loja}</td>
                          <td style={s.td}>
                            <span style={pilula(info.cor)}>{info.rotulo}</span>
                          </td>
                          <td style={{ ...s.td, color: cores.textoSuave }} title={detalheMotivos(l.motivos)}>
                            {textoMotivos(l.motivos, l.estado) || l.mensagem || "—"}
                            {l.reabrivel === false && <div style={s.suave}>não dá para reabrir pelo gestor</div>}
                          </td>
                          <td style={{ ...s.td, whiteSpace: "nowrap" }}>{dataHora(l.inicio)}</td>
                          <td style={{ ...s.td, whiteSpace: "nowrap" }}>
                            {duracaoMin(l.minutos)}
                            {l.emAberto ? (
                              <div style={{ color: cores.vermelho, fontSize: "12px" }}>em aberto</div>
                            ) : (
                              <div style={s.suave}>até {dataHora(l.fim)}</div>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

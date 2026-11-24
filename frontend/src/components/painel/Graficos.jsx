import { useEffect, useRef, useState } from "react";
import { cor } from "./fmt";

// Gráficos do painel em SVG puro (sem biblioteca): linha com cruz de leitura, barras e mapa de calor.
// Regras: um eixo só, linhas de 2px, grade discreta, tooltip ao passar o mouse, legenda quando há 2 séries.

function useLargura() {
  const ref = useRef(null);
  const [largura, setLargura] = useState(600);
  useEffect(() => {
    if (!ref.current) return undefined;
    const ro = new ResizeObserver(([e]) => setLargura(Math.max(240, Math.floor(e.contentRect.width))));
    ro.observe(ref.current);
    return () => ro.disconnect();
  }, []);
  return [ref, largura];
}

function escala(valores) {
  const max = Math.max(0, ...valores.filter((v) => v !== null && v !== undefined));
  if (max === 0) return { max: 1, passos: [0, 1] };
  const bruto = max / 4;
  const mag = 10 ** Math.floor(Math.log10(bruto));
  const passo = [1, 2, 2.5, 5, 10].map((m) => m * mag).find((p) => p >= bruto) || bruto;
  const topo = Math.ceil(max / passo) * passo;
  const passos = [];
  for (let v = 0; v <= topo + 1e-9; v += passo) passos.push(v);
  return { max: topo, passos };
}

function Tooltip({ x, y, largura, linhas }) {
  if (!linhas) return null;
  const esquerda = x > largura / 2;
  return (
    <div
      style={{
        position: "absolute",
        left: esquerda ? undefined : x + 12,
        right: esquerda ? largura - x + 12 : undefined,
        top: Math.max(0, y - 10),
        background: "#F6F3EF",
        border: `1px solid ${cor.borda}`,
        borderRadius: 8,
        padding: "8px 10px",
        fontSize: 12,
        color: cor.texto,
        pointerEvents: "none",
        whiteSpace: "nowrap",
        boxShadow: "0 6px 18px rgba(64, 44, 28, 0.12)",
        zIndex: 2,
      }}
    >
      {linhas.map((l, i) => (
        <div key={i} style={{ display: "flex", alignItems: "center", gap: 6, fontWeight: i === 0 ? 700 : 400, marginTop: i ? 3 : 0 }}>
          {l.cor && <span style={{ width: 10, height: 2, background: l.cor, display: "inline-block" }} />}
          <span style={{ color: i === 0 ? cor.texto : cor.textoSuave }}>{l.rotulo}</span>
          {l.valor !== undefined && <span style={{ marginLeft: "auto", paddingLeft: 10, fontWeight: 600 }}>{l.valor}</span>}
        </div>
      ))}
    </div>
  );
}

export function Legenda({ series }) {
  if (!series || series.length < 2) return null;
  return (
    <div style={{ display: "flex", gap: 14, fontSize: 12, color: cor.textoSuave, marginBottom: 6 }}>
      {series.map((s) => (
        <span key={s.nome} style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
          <span style={{ width: 14, height: 2, background: s.cor, display: "inline-block", borderRadius: 1 }} />
          {s.nome}
        </span>
      ))}
    </div>
  );
}

/**
 * Linha no tempo. series: [{ nome, cor, valores: [n|null] }], rotulos: [string], formato: (n) => string.
 * O último ponto de cada série leva rótulo direto.
 */
/** Linhas. marcas: [{ indice, rotulo }] = pontos verticais (ex.: alteração de cardápio). */
export function GraficoLinha({ series, rotulos, formato, altura = 200, marcas = [] }) {
  const [ref, largura] = useLargura();
  const [foco, setFoco] = useState(null);
  const m = { e: 76, d: 16, t: 16, b: 26 };
  const w = largura - m.e - m.d;
  const h = altura - m.t - m.b;
  const n = rotulos.length;
  const { max, passos } = escala(series.flatMap((s) => s.valores));
  const x = (i) => m.e + (n <= 1 ? w / 2 : (i * w) / (n - 1));
  const y = (v) => m.t + h - (v / max) * h;
  const passoRotulo = Math.max(1, Math.ceil(n / Math.max(2, Math.floor(w / 70))));

  function mover(e) {
    const r = e.currentTarget.getBoundingClientRect();
    const px = e.clientX - r.left;
    const i = Math.round(((px - m.e) / Math.max(1, w)) * (n - 1));
    setFoco(Math.min(n - 1, Math.max(0, i)));
  }

  return (
    <div ref={ref} style={{ position: "relative" }}>
      <Legenda series={series} />
      <svg width={largura} height={altura} onMouseMove={mover} onMouseLeave={() => setFoco(null)} style={{ display: "block" }}>
        {passos.map((p) => (
          <g key={p}>
            <line x1={m.e} x2={largura - m.d} y1={y(p)} y2={y(p)} stroke={cor.grade} strokeWidth={1} />
            <text x={m.e - 8} y={y(p) + 4} textAnchor="end" fontSize={11} fill={cor.textoSuave}>
              {formato(p, true)}
            </text>
          </g>
        ))}
        {rotulos.map((r, i) =>
          i % passoRotulo === 0 || i === n - 1 ? (
            <text key={i} x={x(i)} y={altura - 8} textAnchor="middle" fontSize={11} fill={cor.textoSuave}>
              {r}
            </text>
          ) : null
        )}
        {foco !== null && <line x1={x(foco)} x2={x(foco)} y1={m.t} y2={m.t + h} stroke={cor.textoSuave} strokeDasharray="3 3" />}
        {marcas.map((mc) => (
          <g key={`m${mc.indice}`}>
            <line x1={x(mc.indice)} x2={x(mc.indice)} y1={m.t} y2={m.t + h} stroke={cor.serie2} strokeWidth={1.5} strokeDasharray="4 3" />
            <circle cx={x(mc.indice)} cy={m.t} r={5} fill={cor.serie2} stroke="#FFFFFF" strokeWidth={2}>
              <title>{mc.rotulo}</title>
            </circle>
          </g>
        ))}
        {series.map((s) => {
          const pts = s.valores.map((v, i) => (v === null || v === undefined ? null : [x(i), y(v)]));
          let d = "";
          pts.forEach((p, i) => {
            if (!p) return;
            d += `${!pts[i - 1] ? "M" : "L"}${p[0]},${p[1]}`;
          });
          const ultimo = [...pts].reverse().find(Boolean);
          const iUlt = pts.lastIndexOf(ultimo);
          return (
            <g key={s.nome}>
              <path d={d} fill="none" stroke={s.cor} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />
              {pts.map((p, i) =>
                p && (foco === i || pts.filter(Boolean).length === 1) ? (
                  <circle key={i} cx={p[0]} cy={p[1]} r={4} fill={s.cor} stroke="#FFFFFF" strokeWidth={2} />
                ) : null
              )}
              {ultimo && foco === null && (
                <text x={Math.min(ultimo[0], largura - m.d)} y={Math.max(12, ultimo[1] - 9)} textAnchor="end" fontSize={11} fill={cor.texto} fontWeight={600}>
                  {formato(s.valores[iUlt])}
                </text>
              )}
            </g>
          );
        })}
      </svg>
      {foco !== null && (
        <Tooltip
          x={x(foco)}
          y={m.t}
          largura={largura}
          linhas={[
            { rotulo: rotulos[foco] },
            ...series.map((s) => ({ rotulo: s.nome, cor: s.cor, valor: s.valores[foco] == null ? "—" : formato(s.valores[foco]) })),
            ...marcas.filter((mc) => mc.indice === foco).map((mc) => ({ rotulo: mc.rotulo, cor: cor.serie2, valor: "●" })),
          ]}
        />
      )}
    </div>
  );
}

/** Barras verticais. itens: [{ rotulo, valor, dica }]. */
export function GraficoBarras({ itens, formato, altura = 180, corBarra = cor.serie1 }) {
  const [ref, largura] = useLargura();
  const [foco, setFoco] = useState(null);
  const m = { e: 76, d: 8, t: 10, b: 26 };
  const w = largura - m.e - m.d;
  const h = altura - m.t - m.b;
  const { max, passos } = escala(itens.map((i) => i.valor));
  const banda = w / Math.max(1, itens.length);
  const larguraBarra = Math.max(4, Math.min(36, banda - 2));
  const y = (v) => m.t + h - (v / max) * h;
  const passoRotulo = Math.max(1, Math.ceil(itens.length / Math.max(2, Math.floor(w / 46))));

  return (
    <div ref={ref} style={{ position: "relative" }}>
      <svg width={largura} height={altura} style={{ display: "block" }} onMouseLeave={() => setFoco(null)}>
        {passos.map((p) => (
          <g key={p}>
            <line x1={m.e} x2={largura - m.d} y1={y(p)} y2={y(p)} stroke={cor.grade} />
            <text x={m.e - 8} y={y(p) + 4} textAnchor="end" fontSize={11} fill={cor.textoSuave}>
              {formato(p, true)}
            </text>
          </g>
        ))}
        {itens.map((it, i) => {
          const cx = m.e + banda * i + banda / 2;
          const v = Number(it.valor) || 0;
          const topo = y(v);
          const alt = Math.max(0, m.t + h - topo);
          const r = Math.min(4, larguraBarra / 2, alt);
          const x0 = cx - larguraBarra / 2;
          const d = alt <= 0 ? "" : `M${x0},${m.t + h} V${topo + r} Q${x0},${topo} ${x0 + r},${topo} H${x0 + larguraBarra - r} Q${x0 + larguraBarra},${topo} ${x0 + larguraBarra},${topo + r} V${m.t + h} Z`;
          return (
            <g key={i} onMouseEnter={() => setFoco(i)}>
              <rect x={m.e + banda * i} y={m.t} width={banda} height={h} fill="transparent" />
              <path d={d} fill={corBarra} opacity={foco === null || foco === i ? 1 : 0.45} />
              {(i % passoRotulo === 0 || i === itens.length - 1) && (
                <text x={cx} y={altura - 8} textAnchor="middle" fontSize={11} fill={cor.textoSuave}>
                  {it.rotulo}
                </text>
              )}
            </g>
          );
        })}
      </svg>
      {foco !== null && itens[foco] && (
        <Tooltip
          x={m.e + banda * foco + banda / 2}
          y={y(Number(itens[foco].valor) || 0)}
          largura={largura}
          linhas={[{ rotulo: itens[foco].dica || itens[foco].rotulo }, { rotulo: "Valor", valor: formato(itens[foco].valor) }]}
        />
      )}
    </div>
  );
}

const DIAS = ["Seg", "Ter", "Qua", "Qui", "Sex", "Sáb", "Dom"];
// rampa sequencial azul (do fundo para o mais forte)
const RAMPA = ["#EFEAE4", "#D6E7F5", "#B9D6EE", "#97C2E6", "#73AEDC", "#509AD1", "#3A82BE", "#2D6BA0"];

/** Mapa de calor dia da semana × hora. dados: [{ diaSemana 1-7, hora 0-23, faturamentoMedio }]. */
export function MapaCalor({ dados, formato }) {
  const [ref, largura] = useLargura();
  const [foco, setFoco] = useState(null);
  const horas = dados.map((d) => d.hora);
  const hIni = horas.length ? Math.min(...horas) : 10;
  const hFim = horas.length ? Math.max(...horas) : 23;
  const colunas = hFim - hIni + 1;
  const e = 36;
  const celula = Math.max(10, Math.min(34, Math.floor((largura - e) / Math.max(1, colunas))));
  const altura = 7 * (celula + 2) + 22;
  const mapa = new Map(dados.map((d) => [`${d.diaSemana}-${d.hora}`, Number(d.faturamentoMedio) || 0]));
  const max = Math.max(1, ...mapa.values());

  return (
    <div ref={ref} style={{ position: "relative" }}>
      <svg width={largura} height={altura} onMouseLeave={() => setFoco(null)} style={{ display: "block" }}>
        {DIAS.map((dia, di) => (
          <g key={dia}>
            <text x={0} y={di * (celula + 2) + celula / 2 + 4} fontSize={11} fill={cor.textoSuave}>
              {dia}
            </text>
            {Array.from({ length: colunas }, (_, k) => {
              const hr = hIni + k;
              const v = mapa.get(`${di + 1}-${hr}`) || 0;
              const idx = v <= 0 ? 0 : 1 + Math.min(RAMPA.length - 2, Math.floor((v / max) * (RAMPA.length - 1)));
              return (
                <rect
                  key={hr}
                  x={e + k * (celula + 2)}
                  y={di * (celula + 2)}
                  width={celula}
                  height={celula}
                  rx={3}
                  fill={RAMPA[idx]}
                  stroke={foco && foco.d === di && foco.h === hr ? cor.texto : "none"}
                  onMouseEnter={() => setFoco({ d: di, h: hr, v, x: e + k * (celula + 2) + celula / 2, y: di * (celula + 2) })}
                />
              );
            })}
          </g>
        ))}
        {Array.from({ length: colunas }, (_, k) =>
          (hIni + k) % 2 === 0 ? (
            <text key={k} x={e + k * (celula + 2) + celula / 2} y={altura - 6} textAnchor="middle" fontSize={10} fill={cor.textoSuave}>
              {hIni + k}h
            </text>
          ) : null
        )}
      </svg>
      {foco && (
        <Tooltip
          x={foco.x}
          y={foco.y}
          largura={largura}
          linhas={[{ rotulo: `${DIAS[foco.d]}, ${foco.h}h às ${foco.h + 1}h` }, { rotulo: "Venda média", valor: formato(foco.v) }]}
        />
      )}
    </div>
  );
}

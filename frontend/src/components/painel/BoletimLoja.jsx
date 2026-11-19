import { useState } from "react";
import { painelApi } from "../../services/api";
import { dataHora } from "../monitor/ui";
import { Cartao, Selo } from "./Carteira";
import { botao, caixa, campo, cor, dataCurta, inteiro, minutos, percentual, reais, reaisCurto, rotuloPeriodo, variacao } from "./fmt";
import { GraficoBarras, GraficoLinha, MapaCalor } from "./Graficos";

const META_CONVERSAO = 0.15;

function Bloco({ titulo, subtitulo, children, direita }) {
  return (
    <div style={{ ...caixa, minWidth: 0 }}>
      <div style={{ display: "flex", alignItems: "baseline", gap: 10, marginBottom: 10 }}>
        <div style={{ fontWeight: 700 }}>{titulo}</div>
        {subtitulo && <div style={{ fontSize: 12, color: cor.textoSuave }}>{subtitulo}</div>}
        <div style={{ marginLeft: "auto" }}>{direita}</div>
      </div>
      {children}
    </div>
  );
}

/** Lançamento de conversão e repasse do período (valores do Portal). */
function Lancamento({ dados, onSalvo }) {
  const a = dados.atual || {};
  const [conv, setConv] = useState(a.conversao != null ? String((a.conversao * 100).toFixed(2)).replace(".", ",") : "");
  const [rep, setRep] = useState(a.repasse != null ? Number(a.repasse).toFixed(2).replace(".", ",") : "");
  const [msg, setMsg] = useState("");
  const num = (s) => (s.trim() === "" ? null : Number(s.replace(/\./g, "").replace(",", ".")));

  async function salvar() {
    setMsg("Salvando...");
    try {
      await painelApi.salvarManual({
        chave: dados.chave,
        periodoTipo: dados.periodo.tipo,
        inicio: dados.periodo.inicio,
        conversao: num(conv) == null ? null : num(conv) / 100,
        repasse: num(rep),
      });
      setMsg("Salvo");
      onSalvo();
    } catch (e) {
      setMsg(e.message);
    }
  }

  return (
    <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", fontSize: 13 }}>
      <label>Conversão (%) <input value={conv} onChange={(e) => setConv(e.target.value)} style={{ ...campo, width: 80 }} placeholder="19,2" /></label>
      <label>Repasse (R$) <input value={rep} onChange={(e) => setRep(e.target.value)} style={{ ...campo, width: 120 }} placeholder="31561,74" /></label>
      <button onClick={salvar} style={botao}>Salvar</button>
      <span style={{ color: cor.textoSuave }}>{msg}</span>
    </div>
  );
}

export default function BoletimLoja({ dados, onAtualizar, somenteLeitura = false }) {
  if (!dados) return null;
  const a = dados.atual || {};
  const v = dados.variacao || {};
  const serie = dados.serie || [];
  const rotulos = serie.map((s) => rotuloPeriodo(s.periodo, true));
  const val = (f) => serie.map((s) => (s.metricas ? s.metricas[f] ?? null : null));
  const conversaoBoa = a.conversao != null && a.conversao >= META_CONVERSAO;
  const rec = dados.recordes || {};

  return (
    <div style={{ display: "grid", gap: 16, minWidth: 0, gridTemplateColumns: "minmax(0, 1fr)" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
        <h2 style={{ margin: 0, fontSize: 22 }}>{dados.nome}</h2>
        {dados.plataforma !== "IFOOD" && <span style={{ color: cor.amarelo, fontSize: 12 }}>{dados.plataforma}</span>}
        <Selo fonte={a.fonte} />
        <span style={{ color: cor.textoSuave, fontSize: 13 }}>Período: {rotuloPeriodo(dados.periodo)}</span>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(140px, 100%), 1fr))", gap: 12 }}>
        <Cartao titulo="Faturamento" valor={reaisCurto(a.faturamento)} delta={variacao(v.faturamento)} nota="vs anterior" />
        <Cartao titulo="Pedidos" valor={inteiro(a.pedidos)} delta={variacao(v.pedidos)} nota="vs anterior" />
        <Cartao titulo="Ticket médio" valor={reais(a.ticket)} delta={variacao(v.ticket)} />
        <Cartao
          titulo="Conversão"
          valor={percentual(a.conversao)}
          delta={variacao(v.conversao, { pontos: true })}
          nota={a.conversao == null ? (somenteLeitura ? "ainda não lançada" : "lance abaixo") : conversaoBoa ? "✓ boa (meta 15%)" : "✕ abaixo da meta de 15%"}
          destaque={a.conversao == null ? undefined : conversaoBoa ? "#9FD3B5" : "#F2B79B"}
        />
        <Cartao titulo="Cancelamentos" valor={inteiro(a.cancelamentos)} delta={variacao(v.cancelamentos, { inverso: true })} nota={percentual(a.taxaCancelamento)} />
        <Cartao titulo="Novos clientes" valor={inteiro(a.novosClientes)} delta={variacao(v.novosClientes)} />
        <Cartao titulo="Repasse" valor={reaisCurto(a.repasse)} delta={variacao(v.repasse)}
                nota={a.repasse != null && a.faturamento ? `${percentual(a.repasse / a.faturamento)} do faturamento` : undefined} />
        <Cartao titulo="Tempo de entrega" valor={a.tempoEntrega != null ? `${Math.round(a.tempoEntrega)} min` : "—"} />
        <Cartao titulo="Perda estimada" valor={reaisCurto(a.perdaEstimada)} nota={a.minutosFechada ? `${minutos(a.minutosFechada)} fechada` : "sem fechamentos"}
                destaque={Number(a.perdaEstimada) > 0 ? "#F2B79B" : undefined} />
      </div>

      {!somenteLeitura && (
        <Bloco titulo="Conversão e repasse do período" subtitulo="valores do Portal do Parceiro — ficam salvos para este período">
          <Lancamento key={`${dados.chave}-${dados.periodo.inicio}`} dados={dados} onSalvo={onAtualizar} />
        </Bloco>
      )}

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(380px, 100%), 1fr))", gap: 16 }}>
        <Bloco titulo="Faturamento" subtitulo={`últimos ${serie.length} períodos`}
               direita={<span style={{ fontSize: 12, color: cor.textoSuave }}>média {reaisCurto(rec.mediaFaturamento)} · recorde {reaisCurto(rec.recordeFaturamento)}</span>}>
          <GraficoLinha rotulos={rotulos} formato={(n, eixo) => (eixo ? reaisCurto(n) : reais(n, true))}
                        series={[{ nome: "Faturamento", cor: cor.serie1, valores: val("faturamento") }]} />
        </Bloco>
        <Bloco titulo="Pedidos e novos clientes" subtitulo={`média ${inteiro(rec.mediaPedidos)} · recorde ${inteiro(rec.recordePedidos)} pedidos`}>
          <GraficoLinha rotulos={rotulos} formato={(n) => inteiro(n)}
                        series={[{ nome: "Pedidos", cor: cor.serie1, valores: val("pedidos") }, { nome: "Novos clientes", cor: cor.serie2, valores: val("novosClientes") }]} />
        </Bloco>
        <Bloco titulo="Conversão" subtitulo="meta mínima 15%">
          <GraficoLinha rotulos={rotulos} formato={(n) => percentual(n)}
                        series={[{ nome: "Conversão", cor: cor.serie1, valores: val("conversao") }]} />
        </Bloco>
        <Bloco titulo="Ticket médio">
          <GraficoLinha rotulos={rotulos} formato={(n, eixo) => (eixo ? `R$ ${Math.round(n)}` : reais(n))}
                        series={[{ nome: "Ticket médio", cor: cor.serie1, valores: val("ticket") }]} />
        </Bloco>
      </div>

      {dados.dias?.length > 0 && (
        <Bloco titulo="Faturamento por dia" subtitulo={rotuloPeriodo(dados.periodo)}>
          <GraficoBarras formato={(n, eixo) => (eixo ? reaisCurto(n) : reais(n))}
                         itens={dados.dias.map((d) => ({ rotulo: dataCurta(d.dia), valor: d.faturamento, dica: `${dataCurta(d.dia)} · ${d.pedidos} pedidos` }))} />
        </Bloco>
      )}

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(380px, 100%), 1fr))", gap: 16 }}>
        {dados.curva?.length > 0 && (
          <Bloco titulo="Quando a loja vende" subtitulo="venda média por dia e hora (últimas semanas) — base do cálculo de perdas">
            <MapaCalor dados={dados.curva} formato={(n) => reais(n, true)} />
          </Bloco>
        )}
        <Bloco titulo="Fechamentos no período" subtitulo="loja fechada ou pausada (monitor) × venda média daquela hora">
          {(dados.incidentes || []).length === 0 && <div style={{ color: cor.textoSuave, fontSize: 13 }}>Nenhum fechamento registrado.</div>}
          {(dados.incidentes || []).map((i, k) => (
            <div key={k} style={{ display: "flex", gap: 10, fontSize: 13, padding: "6px 0", borderTop: `1px solid ${cor.grade}` }}>
              <span>{dataHora(i.inicio)} → {dataHora(i.fim)}</span>
              <span style={{ color: cor.textoSuave }}>{minutos(i.minutos)}</span>
              <span style={{ marginLeft: "auto", color: Number(i.perda) > 0 ? cor.ruim : cor.textoSuave, fontWeight: 600 }}>
                {Number(i.perda) > 0 ? `≈ ${reais(i.perda, true)}` : "fora do horário de venda"}
              </span>
            </div>
          ))}
        </Bloco>
      </div>
    </div>
  );
}

import { useCallback, useEffect, useState } from "react";
import { painelApi } from "../services/api";
import BoletimLoja from "../components/painel/BoletimLoja";
import Carteira from "../components/painel/Carteira";
import Coleta from "../components/painel/Coleta";
import Lancamentos from "../components/painel/Lancamentos";
import { campo, cor, moverPeriodo, rotuloPeriodo } from "../components/painel/fmt";

const ABAS = [
  { id: "carteira", rotulo: "Carteira" },
  { id: "loja", rotulo: "Boletim da loja" },
  { id: "lancamentos", rotulo: "Conversão e repasse" },
  { id: "coleta", rotulo: "Dados e coleta" },
];

export default function PainelPage() {
  const [aba, setAba] = useState("carteira");
  const [tipo, setTipo] = useState("SEMANAL");
  const [inicio, setInicio] = useState(null); // null = último período fechado
  const [carteira, setCarteira] = useState(null);
  const [lojaChave, setLojaChave] = useState(null);
  const [loja, setLoja] = useState(null);
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState("");

  const carregarCarteira = useCallback(async () => {
    setCarregando(true);
    setErro("");
    try {
      setCarteira(await painelApi.carteira(tipo, inicio));
    } catch (e) {
      setErro(e.message);
    } finally {
      setCarregando(false);
    }
  }, [tipo, inicio]);

  const carregarLoja = useCallback(async () => {
    if (!lojaChave) return;
    setCarregando(true);
    setErro("");
    try {
      setLoja(await painelApi.loja(lojaChave, tipo, inicio, 12));
    } catch (e) {
      setErro(e.message);
    } finally {
      setCarregando(false);
    }
  }, [lojaChave, tipo, inicio]);

  useEffect(() => {
    if (aba === "carteira" || aba === "lancamentos") carregarCarteira();
  }, [aba, carregarCarteira]);

  useEffect(() => {
    if (aba === "loja") carregarLoja();
  }, [aba, carregarLoja]);

  useEffect(() => {
    if (aba === "loja" && !carteira) carregarCarteira();
  }, [aba, carteira, carregarCarteira]);

  const periodo = (aba === "loja" ? loja?.periodo : carteira?.periodo) || null;

  function trocarTipo(t) {
    setTipo(t);
    setInicio(null);
  }

  function abrirLoja(l) {
    setLojaChave(l.chave);
    setLoja(null);
    setAba("loja");
  }

  const opcoesLoja = (carteira?.linhas || []).slice().sort((a, b) => (a.nome || "").localeCompare(b.nome || ""));

  return (
    <div className="painel" style={{ color: cor.texto, display: "grid", gap: 16, minWidth: 0, gridTemplateColumns: "minmax(0, 1fr)" }}>
      <style>{".painel table, .painel th, .painel td { border: none; background: transparent; }"}</style>
      <div style={{ display: "flex", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
        <div>
          <h1 style={{ margin: 0, fontSize: 24 }}>Painel</h1>
          <div style={{ fontSize: 12, color: cor.textoSuave }}>Resultados das lojas · iFood (Analytics, D-1) + histórico do BI</div>
        </div>
        {aba !== "coleta" && (
          <div style={{ marginLeft: "auto", display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <div style={{ display: "flex", border: `1px solid ${cor.bordaCampo}`, borderRadius: 8, overflow: "hidden" }}>
              {["SEMANAL", "MENSAL"].map((t) => (
                <button key={t} onClick={() => trocarTipo(t)}
                        style={{ padding: "8px 12px", border: "none", cursor: "pointer", fontSize: 13,
                          background: tipo === t ? cor.destaque : cor.campo, color: "#fff", fontWeight: tipo === t ? 700 : 400 }}>
                  {t === "SEMANAL" ? "Semanal" : "Mensal"}
                </button>
              ))}
            </div>
            <button style={navBtn} disabled={!periodo} onClick={() => setInicio(moverPeriodo(periodo, -1))}>◀</button>
            <div style={{ minWidth: 150, textAlign: "center", fontWeight: 600 }}>
              {periodo ? rotuloPeriodo(periodo) : "—"}
            </div>
            <button style={navBtn} disabled={!periodo} onClick={() => setInicio(moverPeriodo(periodo, 1))}>▶</button>
            {inicio && <button style={{ ...navBtn, fontSize: 12 }} onClick={() => setInicio(null)}>Último fechado</button>}
          </div>
        )}
      </div>

      <div style={{ display: "flex", gap: 4, borderBottom: `1px solid ${cor.borda}`, flexWrap: "wrap" }}>
        {ABAS.map((a) => (
          <button key={a.id} onClick={() => setAba(a.id)}
                  style={{ background: "none", border: "none", cursor: "pointer", padding: "10px 14px", fontSize: 14,
                    color: aba === a.id ? cor.texto : cor.textoSuave, fontWeight: aba === a.id ? 700 : 400,
                    borderBottom: `2px solid ${aba === a.id ? cor.destaque : "transparent"}` }}>
            {a.rotulo}
          </button>
        ))}
        {aba === "loja" && (
          <select value={lojaChave || ""} onChange={(e) => { setLojaChave(e.target.value); setLoja(null); }}
                  style={{ ...campo, marginLeft: "auto", maxWidth: 320, alignSelf: "center" }}>
            <option value="">Escolha a loja...</option>
            {opcoesLoja.map((l) => <option key={l.chave} value={l.chave}>{l.nome}</option>)}
          </select>
        )}
      </div>

      {erro && <div style={{ color: cor.ruim, fontSize: 13 }}>{erro}</div>}
      {carregando && <div style={{ color: cor.textoSuave, fontSize: 13 }}>Carregando...</div>}

      {aba === "carteira" && <Carteira dados={carteira} onAbrirLoja={abrirLoja} />}
      {aba === "loja" && (lojaChave
        ? <BoletimLoja dados={loja} onAtualizar={carregarLoja} />
        : <div style={{ color: cor.textoSuave }}>Escolha uma loja acima, ou clique numa loja na aba Carteira.</div>)}
      {aba === "lancamentos" && <Lancamentos dados={carteira} onSalvo={carregarCarteira} />}
      {aba === "coleta" && <Coleta />}
    </div>
  );
}

const navBtn = {
  background: cor.campo,
  border: `1px solid ${cor.bordaCampo}`,
  color: cor.texto,
  borderRadius: 8,
  padding: "7px 10px",
  cursor: "pointer",
};

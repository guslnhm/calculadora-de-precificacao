import { useCallback, useEffect, useState } from "react";
import { acessosApi } from "../services/api";
import { cor } from "../components/painel/fmt";
import AbaAdesao from "../components/acesso/AbaAdesao";
import AbaConvites from "../components/acesso/AbaConvites";
import AbaUsuarios from "../components/acesso/AbaUsuarios";

// Acessos ao site: convites para mentorados, usuários e adesão.

const ABAS = [
  { id: "adesao", rotulo: "Adesão" },
  { id: "convites", rotulo: "Convites" },
  { id: "usuarios", rotulo: "Usuários" },
];

export default function AcessosPage() {
  const [aba, setAba] = useState("adesao");
  const [lojas, setLojas] = useState([]);
  const [convites, setConvites] = useState([]);
  const [usuarios, setUsuarios] = useState([]);
  const [adesao, setAdesao] = useState(null);
  const [erro, setErro] = useState("");

  const carregar = useCallback(() => {
    Promise.all([acessosApi.lojas(), acessosApi.convites(), acessosApi.usuarios(), acessosApi.adesao()])
      .then(([l, c, u, a]) => {
        setLojas(l);
        setConvites(c);
        setUsuarios(u);
        setAdesao(a);
        setErro("");
      })
      .catch((e) => setErro(e.message));
  }, []);

  useEffect(() => {
    carregar();
  }, [carregar]);

  return (
    <div style={{ color: cor.texto, display: "grid", gap: 16, gridTemplateColumns: "minmax(0, 1fr)" }}>
      <style>{".acessos table, .acessos th, .acessos td { border: none; background: transparent; }"}</style>
      <div>
        <h1 style={{ margin: 0, fontSize: 24 }}>Acessos</h1>
        <div style={{ fontSize: 12, color: cor.textoSuave }}>
          Links de cadastro para mentorados, lojas de cada um e quem está usando o painel
        </div>
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
      </div>
      {erro && <div style={{ color: cor.ruim, fontSize: 13 }}>{erro}</div>}
      <div className="acessos">
        {aba === "adesao" && <AbaAdesao adesao={adesao} usuarios={usuarios} />}
        {aba === "convites" && <AbaConvites lojas={lojas} convites={convites} onMudou={carregar} />}
        {aba === "usuarios" && <AbaUsuarios usuarios={usuarios} lojas={lojas} onMudou={carregar} />}
      </div>
    </div>
  );
}

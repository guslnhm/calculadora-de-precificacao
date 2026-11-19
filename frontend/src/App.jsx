/*import { Link, Route, Routes, useLocation } from "react-router-dom";
import LojasPage from "./pages/LojasPage";
import ItensPage from "./pages/ItensPage";
import SimulacaoPage from "./pages/SimulacaoPage";

function NavLink({ to, children }) {
  const location = useLocation();
  const ativo = location.pathname === to;

  return (
    <Link
      to={to}
      style={{
        ...styles.link,
        ...(ativo ? styles.linkAtivo : {}),
      }}
    >
      {children}
    </Link>
  );
}

export default function App() {
  return (
    <div style={styles.container}>
      <header style={styles.header}>
        <div style={styles.headerInner}>
          <h1 style={styles.titulo}>Calculadora de Precificação</h1>
          <nav style={styles.nav}>
            <NavLink to="/">Lojas</NavLink>
            <NavLink to="/itens">Itens</NavLink>
            <NavLink to="/simulacao">Simulação</NavLink>
          </nav>
        </div>
      </header>

      <main style={styles.main}>
        <div style={styles.mainInner}>
          <Routes>
            <Route path="/" element={<LojasPage />} />
            <Route path="/itens" element={<ItensPage />} />
            <Route path="/simulacao" element={<SimulacaoPage />} />
          </Routes>
        </div>
      </main>
    </div>
  );
}

const styles = {
  container: {
    fontFamily: "Arial, sans-serif",
    minHeight: "100vh",
    backgroundColor: "#f4f6f8",
  },
  header: {
    backgroundColor: "#1f2937",
    color: "#fff",
    padding: "16px 24px",
  },
  headerInner: {
    maxWidth: "1100px",
    margin: "0 auto",
  },
  titulo: {
    margin: 0,
    fontSize: "28px",
  },
  nav: {
    display: "flex",
    gap: "12px",
    marginTop: "14px",
    flexWrap: "wrap",
  },
  link: {
    color: "#fff",
    textDecoration: "none",
    padding: "8px 12px",
    borderRadius: "8px",
    backgroundColor: "transparent",
  },
  linkAtivo: {
    backgroundColor: "#374151",
  },
  main: {
    padding: "24px",
  },
  mainInner: {
    maxWidth: "1100px",
    margin: "0 auto",
  },
};*/

import { useEffect, useState } from "react";
import { Link, Navigate, Route, Routes, useLocation } from "react-router-dom";
import LojasPage from "./pages/LojasPage";
import ItensPage from "./pages/ItensPage";
import SimulacaoPage from "./pages/SimulacaoPage";
import LoginPage from "./pages/LoginPage";
import SimulacaoReversaPage from "./pages/SimulacaoReversaPage";
import Simulacao99Page from "./pages/Simulacao99Page";
import MonitorPage from "./pages/MonitorPage";
import CardapioPage from "./pages/CardapioPage";
import ReajustePage from "./pages/ReajustePage";
import LojaIfoodPage from "./pages/LojaIfoodPage";
import PainelPage from "./pages/PainelPage";
import AcessosPage from "./pages/AcessosPage";
import ConvitePage from "./pages/ConvitePage";
import NovaSenhaPage from "./pages/NovaSenhaPage";
import TrocarSenha from "./components/acesso/TrocarSenha";
import { authApi, limparSessao } from "./services/api";

function NavLink({ to, children }) {
  const location = useLocation();
  const ativo = location.pathname === to;

  return (
    <Link
      to={to}
      style={{
        ...styles.link,
        ...(ativo ? styles.linkAtivo : {}),
      }}
    >
      {children}
    </Link>
  );
}

export default function App() {
  const location = useLocation();
  const [autenticado, setAutenticado] = useState(!!localStorage.getItem("token"));

  // páginas abertas sem login: cadastro pelo convite e link de nova senha
  if (location.pathname.startsWith("/convite/") || location.pathname.startsWith("/nova-senha/")) {
    return (
      <Routes>
        <Route path="/convite/:token" element={<ConvitePage onEntrar={() => setAutenticado(true)} />} />
        <Route path="/nova-senha/:token" element={<NovaSenhaPage onEntrar={() => setAutenticado(true)} />} />
      </Routes>
    );
  }

  if (!autenticado) {
    return <LoginPage onLogin={() => setAutenticado(true)} />;
  }

  return <AreaLogada onSair={() => setAutenticado(false)} />;
}

function AreaLogada({ onSair }) {
  const [eu, setEu] = useState(null);
  const [trocandoSenha, setTrocandoSenha] = useState(false);
  const usuarioNome = eu?.nome || localStorage.getItem("usuarioNome");
  const mentorado = (eu?.perfil || localStorage.getItem("perfil")) === "MENTORADO";

  useEffect(() => {
    authApi.eu().then((d) => {
      setEu(d);
      localStorage.setItem("perfil", d.perfil || "");
      localStorage.setItem("usuarioNome", d.nome || "");
    }).catch(() => {});
  }, []);

  async function handleLogout() {
    try {
      await authApi.sair();
    } catch {
      // sai mesmo assim
    }
    limparSessao();
    onSair();
  }

  const senhaObrigatoria = !!eu?.trocarSenha;

  return (
    <div style={styles.container}>
      {(trocandoSenha || senhaObrigatoria) && (
        <TrocarSenha obrigatoria={senhaObrigatoria} onFechar={(trocou) => {
          setTrocandoSenha(false);
          if (trocou) setEu((e) => ({ ...e, trocarSenha: false }));
        }} />
      )}
      <header style={styles.header}>
        <div style={styles.headerInner}>
          <div style={styles.topoHeader}>
            <h1 style={styles.titulo}>{mentorado ? "Painel de resultados" : "Calculadora de Precificação"}</h1>
            <div style={styles.usuarioBox}>
              <span>{usuarioNome}</span>
              <button style={styles.botaoTopo} onClick={() => setTrocandoSenha(true)}>Trocar senha</button>
              <button style={styles.botaoTopo} onClick={handleLogout}>Sair</button>
            </div>
          </div>

          {!mentorado && (
            <nav style={styles.nav}>
              <NavLink to="/painel">Painel</NavLink>
              <NavLink to="/">Lojas</NavLink>
              <NavLink to="/itens">Itens</NavLink>
              <NavLink to="/simulacao">Calculadora iFood</NavLink>
              <NavLink to="/simulacao-reversa">Calculadora Reversa iFood</NavLink>
              <NavLink to="/simulacao-99">Calculadora 99Food</NavLink>
              <NavLink to="/loja-ifood">Loja iFood</NavLink>
              <NavLink to="/monitor">Monitor iFood</NavLink>
              <NavLink to="/cardapio">Cardápio iFood</NavLink>
              <NavLink to="/reajuste">Reajuste iFood</NavLink>
              <NavLink to="/acessos">Acessos</NavLink>
            </nav>
          )}
        </div>
      </header>

      <main style={styles.main}>
        <div style={styles.mainInner}>
          {mentorado ? (
            <Routes>
              <Route path="/painel" element={<PainelPage mentorado />} />
              <Route path="*" element={<Navigate to="/painel" replace />} />
            </Routes>
          ) : (
            <Routes>
              <Route path="/" element={<LojasPage />} />
              <Route path="/itens" element={<ItensPage />} />
              <Route path="/simulacao" element={<SimulacaoPage />} />
              <Route path="/simulacao-reversa" element={<SimulacaoReversaPage />} />
              <Route
                path="/simulacao-99"
                element={<Simulacao99Page />}
              />
              <Route path="/painel" element={<PainelPage />} />
              <Route path="/monitor" element={<MonitorPage />} />
              <Route path="/loja-ifood" element={<LojaIfoodPage />} />
              <Route path="/cardapio" element={<CardapioPage />} />
              <Route path="/reajuste" element={<ReajustePage />} />
              <Route path="/acessos" element={<AcessosPage />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          )}
        </div>
      </main>
    </div>
  );
}

const styles = {
  container: {
    fontFamily: "Arial, sans-serif",
    minHeight: "100vh",
    backgroundColor: "#0D1117",
  },
  header: {
    backgroundColor: "#0D1117",
    color: "#F6F8FA",
    padding: "18px 24px",
    borderBottom: "1px solid #1B222B",
  },
  headerInner: {
    maxWidth: "1100px",
    margin: "0 auto",
  },
  topoHeader: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    gap: "16px",
    flexWrap: "wrap",
  },
  titulo: {
    margin: 0,
    fontSize: "clamp(20px, 5vw, 28px)",
  },
  usuarioBox: {
    display: "flex",
    alignItems: "center",
    gap: "10px",
    flexWrap: "wrap",
  },
  botaoTopo: {
    background: "#21262D",
    border: "1px solid #30363D",
    color: "#F6F8FA",
    borderRadius: "8px",
    padding: "6px 10px",
    cursor: "pointer",
    fontSize: "13px",
  },
  nav: {
    display: "flex",
    gap: "12px",
    marginTop: "14px",
    flexWrap: "wrap",
  },
  link: {
    color: "#fff",
    textDecoration: "none",
    padding: "8px 12px",
    borderRadius: "8px",
    backgroundColor: "transparent",
  },
  linkAtivo: {
    backgroundColor: "#161B22",
  },
  main: {
    padding: "24px",
    backgroundColor: "#0D1117",
  },
  mainInner: {
    maxWidth: "1100px",
    margin: "0 auto",
  },
};
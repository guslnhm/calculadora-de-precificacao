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

import { useState } from "react";
import { Link, Route, Routes, useLocation } from "react-router-dom";
import LojasPage from "./pages/LojasPage";
import ItensPage from "./pages/ItensPage";
import SimulacaoPage from "./pages/SimulacaoPage";
import LoginPage from "./pages/LoginPage";
import SimulacaoReversaPage from "./pages/SimulacaoReversaPage";
import Simulacao99Page from "./pages/Simulacao99Page";

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
  const [autenticado, setAutenticado] = useState(!!localStorage.getItem("token"));
  const usuarioNome = localStorage.getItem("usuarioNome");

  function handleLogout() {
    localStorage.removeItem("token");
    localStorage.removeItem("usuarioNome");
    setAutenticado(false);
  }

  if (!autenticado) {
    return <LoginPage onLogin={() => setAutenticado(true)} />;
  }

  return (
    <div style={styles.container}>
      <header style={styles.header}>
        <div style={styles.headerInner}>
          <div style={styles.topoHeader}>
            <h1 style={styles.titulo}>Calculadora de Precificação</h1>
            <div style={styles.usuarioBox}>
              <span>{usuarioNome}</span>
              <button onClick={handleLogout}>Sair</button>
            </div>
          </div>

          <nav style={styles.nav}>
            <NavLink to="/">Lojas</NavLink>
            <NavLink to="/itens">Itens</NavLink>
            <NavLink to="/simulacao">Calculadora iFood</NavLink>
            <NavLink to="/simulacao-reversa">Calculadora Reversa iFood</NavLink>
            <NavLink to="/simulacao-99">Calculadora 99Food</NavLink>
          </nav>
        </div>
      </header>

      <main style={styles.main}>
        <div style={styles.mainInner}>
          <Routes>
            <Route path="/" element={<LojasPage />} />
            <Route path="/itens" element={<ItensPage />} />
            <Route path="/simulacao" element={<SimulacaoPage />} />
            <Route path="/simulacao-reversa" element={<SimulacaoReversaPage />} />
            <Route
              path="/simulacao-99"
              element={<Simulacao99Page />}
            />
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
    fontSize: "28px",
  },
  usuarioBox: {
    display: "flex",
    alignItems: "center",
    gap: "10px",
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
import { useState } from "react";
import { login, salvarSessao } from "../services/api";
import logo from "../assets/rizq_logo_branco.png";
import ceu from "../assets/ceu.jpg";

// Login: céu do brandbook RIZQ de um lado, formulário do outro. No celular o céu vira uma faixa em cima.

export default function LoginPage({ onLogin }) {
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [erro, setErro] = useState("");
  const [carregando, setCarregando] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();

    try {
      setErro("");
      setCarregando(true);

      const data = await login({ email, senha });

      salvarSessao(data);
      onLogin(data);
    } catch (err) {
      setErro(err.message);
    } finally {
      setCarregando(false);
    }
  }

  return (
    <div style={styles.container} className="tela-login">
      <style>{`
        @media (max-width: 820px) {
          .tela-login { grid-template-columns: minmax(0, 1fr) !important; }
          .tela-login .login-ceu { min-height: 240px !important; padding: 28px 20px !important; }
          .tela-login .login-ceu h1 { font-size: 28px !important; }
          .tela-login .login-form { padding: 28px 20px 40px !important; align-content: start !important; }
        }
      `}</style>

      <div style={styles.ceu} className="login-ceu">
        <img src={logo} alt="RIZQ" style={styles.logo} />
        <div>
          <h1 style={styles.title}>Resultados e precificação</h1>
          <p style={styles.subtitle}>
            Acompanhe os resultados das lojas no iFood, atualizados todo dia, e as
            ferramentas de precificação da equipe.
          </p>
        </div>
      </div>

      <div style={styles.right} className="login-form">
        <form onSubmit={handleSubmit} style={styles.card}>
          <h2 style={styles.cardTitle}>Entrar</h2>

          <div style={styles.field}>
            <label style={styles.label} htmlFor="login-email">E-mail</label>
            <input
              id="login-email"
              style={styles.input}
              value={email}
              autoComplete="username"
              inputMode="email"
              autoCapitalize="none"
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          <div style={styles.field}>
            <label style={styles.label} htmlFor="login-senha">Senha</label>
            <input
              id="login-senha"
              style={styles.input}
              type="password"
              autoComplete="current-password"
              value={senha}
              onChange={(e) => setSenha(e.target.value)}
            />
          </div>

          {erro && <div style={styles.error}>{erro}</div>}

          <button style={styles.button} disabled={carregando}>
            {carregando ? "Entrando..." : "Entrar"}
          </button>

          <p style={styles.ajuda}>
            Esqueceu a senha ou ainda não tem acesso? Fale com a sua mentoria.
          </p>
        </form>
      </div>
    </div>
  );
}

const styles = {
  container: {
    minHeight: "100vh",
    backgroundColor: "#FFFFFF",
    display: "grid",
    gridTemplateColumns: "minmax(0, 1.1fr) minmax(0, 1fr)",
    color: "#2A2019",
  },
  ceu: {
    background: `linear-gradient(0deg, rgba(36, 96, 150, 0.6) 0%, rgba(36, 96, 150, 0) 55%), #509AD1 url(${ceu}) center / cover`,
    color: "#FFFFFF",
    padding: "48px",
    display: "flex",
    flexDirection: "column",
    justifyContent: "space-between",
    gap: "32px",
  },
  logo: {
    width: "180px",
    maxWidth: "50vw",
    height: "auto",
    display: "block",
  },
  title: {
    margin: 0,
    fontSize: "clamp(30px, 3.6vw, 44px)",
    fontWeight: 400,
    lineHeight: 1.15,
    color: "#FFFFFF",
  },
  subtitle: {
    margin: "12px 0 0",
    fontSize: "17px",
    lineHeight: 1.55,
    fontWeight: 300,
    maxWidth: "40ch",
  },
  right: {
    display: "grid",
    alignContent: "center",
    justifyItems: "center",
    padding: "48px",
  },
  card: {
    width: "100%",
    maxWidth: "400px",
    display: "grid",
    gap: "16px",
  },
  cardTitle: {
    margin: "0 0 4px",
    fontSize: "26px",
  },
  field: {
    display: "grid",
    gap: "6px",
  },
  label: {
    fontSize: "13px",
    color: "#76675C",
  },
  input: {
    width: "100%",
    padding: "12px 14px",
    borderRadius: "10px",
    border: "1px solid #DCD3C9",
    background: "#FBF9F6",
    color: "#2A2019",
    fontSize: "16px",
    outline: "none",
  },
  error: {
    background: "rgba(232, 110, 54, 0.12)",
    border: "1px solid rgba(201, 83, 30, 0.35)",
    color: "#B14A18",
    padding: "10px 12px",
    borderRadius: "10px",
    fontSize: "14px",
  },
  button: {
    marginTop: "4px",
    backgroundColor: "#2D73AC",
    border: "none",
    padding: "13px 16px",
    borderRadius: "10px",
    color: "#FFFFFF",
    fontWeight: 600,
    fontSize: "16px",
    cursor: "pointer",
  },
  ajuda: {
    margin: 0,
    color: "#76675C",
    fontSize: "13px",
    lineHeight: 1.5,
  },
};

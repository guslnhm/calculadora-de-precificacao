import { useState } from "react";
import { login } from "../services/api";
import logo from "../assets/ssd_logo.png";

export default function LoginPage({ onLogin }) {
  const [email, setEmail] = useState("admin");
  const [senha, setSenha] = useState("admin");
  const [erro, setErro] = useState("");
  const [carregando, setCarregando] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();

    try {
      setErro("");
      setCarregando(true);

      const data = await login({ email, senha });

      localStorage.setItem("token", data.token);
      localStorage.setItem("usuarioNome", data.nome);

      onLogin();
    } catch (err) {
      setErro(err.message);
    } finally {
      setCarregando(false);
    }
  }

  return (
    <div style={styles.container}>
      <div style={styles.backgroundGlowTop}></div>
      <div style={styles.backgroundGlowBottom}></div>

      <div style={styles.left}>
        <div style={styles.brandRow}>
          <img src={logo} alt="Seu Sucesso Delivery" style={styles.logo} />
          <div style={styles.brandBadge}>Sistema interno</div>
        </div>

        <h1 style={styles.title}>
          Calculadora de
          <br />
          Precificação
        </h1>

        <p style={styles.subtitle}>
          Plataforma interna para cadastrar lojas, itens com CMV e simular preços
          de forma rápida, padronizada e segura.
        </p>

        <div style={styles.infoBox}>
          <div style={styles.infoItem}>
            <div style={styles.infoDot}></div>
            <span>Simulação rápida de precificação</span>
          </div>

          <div style={styles.infoItem}>
            <div style={styles.infoDot}></div>
            <span>Cadastro centralizado por loja</span>
          </div>

          <div style={styles.infoItem}>
            <div style={styles.infoDot}></div>
            <span>Acesso restrito por login</span>
          </div>
        </div>
      </div>

      <div style={styles.right}>
        <form onSubmit={handleSubmit} style={styles.card}>
          <div style={styles.cardHeader}>
            <h2 style={styles.cardTitle}>Acessar sistema</h2>
            <p style={styles.cardSubtitle}>
              Entre com suas credenciais para continuar.
            </p>
          </div>

          <div style={styles.field}>
            <label style={styles.label}>Usuário</label>
            <input
              style={styles.input}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          <div style={styles.field}>
            <label style={styles.label}>Senha</label>
            <input
              style={styles.input}
              type="password"
              value={senha}
              onChange={(e) => setSenha(e.target.value)}
            />
          </div>

          {erro && <div style={styles.error}>{erro}</div>}

          <button style={styles.button} disabled={carregando}>
            {carregando ? "Entrando..." : "Entrar"}
          </button>
        </form>
      </div>
    </div>
  );
}

const styles = {
  container: {
    minHeight: "100vh",
    backgroundColor: "#0D1117",
    display: "grid",
    gridTemplateColumns: "1.2fr 0.9fr",
    alignItems: "center",
    gap: "40px",
    padding: "60px",
    color: "#F6F8FA",
    fontFamily: "Inter, Arial, sans-serif",
    position: "relative",
    overflow: "hidden",
  },

  backgroundGlowTop: {
    position: "absolute",
    width: "380px",
    height: "380px",
    borderRadius: "50%",
    background: "rgba(230, 57, 70, 0.12)",
    top: "-100px",
    right: "-80px",
    filter: "blur(70px)",
    pointerEvents: "none",
  },

  backgroundGlowBottom: {
    position: "absolute",
    width: "420px",
    height: "420px",
    borderRadius: "50%",
    background: "rgba(169, 204, 227, 0.10)",
    bottom: "-140px",
    left: "-100px",
    filter: "blur(80px)",
    pointerEvents: "none",
  },

  left: {
    maxWidth: "620px",
    position: "relative",
    zIndex: 1,
    display: "flex",
    flexDirection: "column",
    justifyContent: "center",
  },

  brandRow: {
    display: "flex",
    alignItems: "center",
    gap: "18px",
    marginBottom: "20px",
  },

  logo: {
    width: "124px",
    height: "auto",
    display: "block",
    filter: "drop-shadow(0 6px 24px rgba(230,57,70,0.20))",
    marginTop: "-6px",
  },

  brandBadge: {
    display: "inline-flex",
    alignItems: "center",
    backgroundColor: "#161B22",
    color: "#A9CCE3",
    border: "1px solid #26313B",
    padding: "8px 14px",
    borderRadius: "999px",
    fontSize: "14px",
    whiteSpace: "nowrap",
  },

  title: {
    fontSize: "56px",
    lineHeight: "1.02",
    margin: "0 0 18px 0",
    letterSpacing: "-1px",
  },

  subtitle: {
    color: "#D9D9D9",
    fontSize: "18px",
    lineHeight: "1.6",
    maxWidth: "520px",
    margin: "0 0 28px 0",
  },

  infoBox: {
    display: "grid",
    gap: "12px",
    marginTop: "6px",
  },

  infoItem: {
    display: "flex",
    alignItems: "center",
    gap: "10px",
    color: "#F6F8FA",
    fontSize: "16px",
  },

  infoDot: {
    width: "10px",
    height: "10px",
    borderRadius: "50%",
    backgroundColor: "#E63946",
    flexShrink: 0,
  },

  right: {
    display: "flex",
    justifyContent: "center",
    position: "relative",
    zIndex: 1,
  },

  card: {
    width: "100%",
    maxWidth: "430px",
    backgroundColor: "#161B22",
    border: "1px solid #26313B",
    padding: "32px",
    borderRadius: "20px",
    display: "flex",
    flexDirection: "column",
    gap: "18px",
    boxShadow: "0 20px 60px rgba(0,0,0,0.45)",
  },

  cardHeader: {
    marginBottom: "6px",
  },

  cardTitle: {
    margin: 0,
    fontSize: "30px",
    color: "#F6F8FA",
  },

  cardSubtitle: {
    margin: "8px 0 0 0",
    color: "#D9D9D9",
    lineHeight: "1.5",
  },

  field: {
    display: "grid",
    gap: "8px",
  },

  label: {
    color: "#D9D9D9",
    fontSize: "14px",
  },

  input: {
    backgroundColor: "#21262D",
    border: "1px solid #30363D",
    padding: "12px 14px",
    borderRadius: "10px",
    color: "#F6F8FA",
    outline: "none",
    fontSize: "15px",
  },

  error: {
    color: "#E63946",
    fontWeight: "bold",
    fontSize: "14px",
  },

  button: {
    marginTop: "6px",
    backgroundColor: "#E63946",
    border: "none",
    padding: "13px 16px",
    borderRadius: "10px",
    color: "#F6F8FA",
    fontSize: "16px",
    fontWeight: "bold",
    cursor: "pointer",
  },
};
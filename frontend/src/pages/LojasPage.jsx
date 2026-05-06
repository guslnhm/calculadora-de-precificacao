import { useEffect, useState } from "react";
import { criarLoja, listarLojas } from "../services/api";

export default function LojasPage() {
  const [lojas, setLojas] = useState([]);
  const [nome, setNome] = useState("");
  const [observacao, setObservacao] = useState("");
  const [erro, setErro] = useState("");
  const [mensagem, setMensagem] = useState("");
  const [carregando, setCarregando] = useState(false);

  async function carregarLojas() {
    try {
      setErro("");
      const data = await listarLojas();
      setLojas(data);
    } catch (error) {
      setErro(error.message);
    }
  }

  useEffect(() => {
    carregarLojas();
  }, []);

  async function handleSubmit(event) {
    event.preventDefault();

    try {
      setCarregando(true);
      setErro("");
      setMensagem("");

      await criarLoja({
        nome,
        observacao,
      });

      setNome("");
      setObservacao("");
      setMensagem("Loja cadastrada com sucesso.");

      await carregarLojas();
    } catch (error) {
      setErro(error.message);
    } finally {
      setCarregando(false);
    }
  }

  return (
    <div style={styles.page}>
      <div style={styles.pageHeader}>
        <h2 style={styles.pageTitle}>Lojas</h2>
        <p style={styles.pageSubtitle}>
          Cadastre e visualize as lojas que serão usadas na precificação.
        </p>
      </div>

      <div style={styles.layout}>
        <form onSubmit={handleSubmit} style={styles.formCard}>
          <h3 style={styles.cardTitle}>Cadastrar loja</h3>

          <div style={styles.field}>
            <label style={styles.label}>Nome da loja</label>
            <input
              style={styles.input}
              type="text"
              value={nome}
              onChange={(e) => setNome(e.target.value)}
              required
            />
          </div>

          <div style={styles.field}>
            <label style={styles.label}>Observação</label>
            <textarea
              style={styles.textarea}
              value={observacao}
              onChange={(e) => setObservacao(e.target.value)}
            />
          </div>

          <button type="submit" disabled={carregando} style={styles.primaryButton}>
            {carregando ? "Salvando..." : "Cadastrar loja"}
          </button>

          {erro && <p style={styles.erro}>{erro}</p>}
          {mensagem && <p style={styles.sucesso}>{mensagem}</p>}
        </form>

        <div style={styles.tableCard}>
          <div style={styles.tableHeader}>
            <h3 style={styles.cardTitle}>Lista de lojas</h3>
            <span style={styles.badge}>{lojas.length} cadastrada(s)</span>
          </div>

          {lojas.length === 0 ? (
            <p style={styles.emptyText}>Nenhuma loja cadastrada.</p>
          ) : (
            <table style={styles.table}>
              <thead>
                <tr>
                  <th>Nome</th>
                  <th>Observação</th>
                </tr>
              </thead>
              <tbody>
                {lojas.map((loja) => (
                  <tr key={loja.id}>
                    <td>{loja.nome}</td>
                    <td>{loja.observacao || "-"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
}

const styles = {
  page: {
    color: "#F6F8FA",
  },

  pageHeader: {
    marginBottom: "24px",
  },

  pageTitle: {
    marginBottom: "8px",
  },

  pageSubtitle: {
    margin: 0,
    color: "#D9D9D9",
  },

  layout: {
    display: "grid",
    gridTemplateColumns: "380px 1fr",
    gap: "20px",
    alignItems: "start",
  },

  formCard: {
    backgroundColor: "#161B22",
    border: "1px solid #26313B",
    borderRadius: "18px",
    padding: "22px",
    display: "grid",
    gap: "14px",
    boxShadow: "0 14px 40px rgba(0,0,0,0.25)",
  },

  tableCard: {
    backgroundColor: "#161B22",
    border: "1px solid #26313B",
    borderRadius: "18px",
    padding: "22px",
    boxShadow: "0 14px 40px rgba(0,0,0,0.25)",
  },

  tableHeader: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    gap: "12px",
    marginBottom: "16px",
    flexWrap: "wrap",
  },

  cardTitle: {
    margin: 0,
  },

  badge: {
    backgroundColor: "#1B222B",
    color: "#A9CCE3",
    border: "1px solid #30363D",
    borderRadius: "999px",
    padding: "6px 12px",
    fontSize: "13px",
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

  textarea: {
    backgroundColor: "#21262D",
    border: "1px solid #30363D",
    padding: "12px 14px",
    borderRadius: "10px",
    color: "#F6F8FA",
    outline: "none",
    fontSize: "15px",
    minHeight: "110px",
    resize: "vertical",
  },

  primaryButton: {
    backgroundColor: "#E63946",
    color: "#F6F8FA",
    border: "none",
    padding: "12px 18px",
    borderRadius: "10px",
    fontWeight: "bold",
    cursor: "pointer",
  },

  erro: {
    color: "#E63946",
    fontWeight: "bold",
    margin: 0,
  },

  sucesso: {
    color: "#A9CCE3",
    fontWeight: "bold",
    margin: 0,
  },

  emptyText: {
    color: "#D9D9D9",
    margin: 0,
  },

  table: {
    width: "100%",
    borderCollapse: "collapse",
    color: "#F6F8FA",
    overflow: "hidden",
  },
};
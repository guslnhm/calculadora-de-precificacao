import { useEffect, useState } from "react";
import {
  criarItem,
  editarItem,
  desativarItem,
  importarItensPlanilha,
  listarItens,
  listarLojas,
} from "../services/api";

function formatarMoeda(valor) {
  return Number(valor).toLocaleString("pt-BR", {
    style: "currency",
    currency: "BRL",
  });
}

export default function ItensPage() {
  const [lojas, setLojas] = useState([]);
  const [itens, setItens] = useState([]);
  const [lojaId, setLojaId] = useState("");
  const [nomeItem, setNomeItem] = useState("");
  const [cmv, setCmv] = useState("");
  const [observacao, setObservacao] = useState("");
  const [filtroLojaId, setFiltroLojaId] = useState("");
  const [erro, setErro] = useState("");
  const [mensagem, setMensagem] = useState("");
  const [carregando, setCarregando] = useState(false);
  const [lojaImportacaoId, setLojaImportacaoId] = useState("");
  const [arquivoImportacao, setArquivoImportacao] = useState(null);
  const [carregandoImportacao, setCarregandoImportacao] = useState(false);
  const [itemEditandoId, setItemEditandoId] = useState(null);

  async function carregarLojas() {
    try {
      const data = await listarLojas();
      setLojas(data);
    } catch (error) {
      setErro(error.message);
    }
  }

  async function carregarItens(lojaIdFiltro = "") {
    try {
      setErro("");
      const data = await listarItens(lojaIdFiltro || undefined);
      setItens(data);
    } catch (error) {
      setErro(error.message);
    }
  }

  useEffect(() => {
    carregarLojas();
    carregarItens();
  }, []);

  async function handleSubmit(event) {
  event.preventDefault();

  try {
    setCarregando(true);
    setErro("");
    setMensagem("");

    const dados = {
      lojaId: Number(lojaId),
      nomeItem,
      cmv: Number(cmv),
      observacao,
    };

    if (itemEditandoId) {
      await editarItem(itemEditandoId, dados);
      setMensagem("Item atualizado com sucesso.");
    } else {
      await criarItem(dados);
      setMensagem("Item cadastrado com sucesso.");
    }

    limparFormularioItem();
    await carregarItens(filtroLojaId);
  } catch (error) {
    setErro(error.message);
  } finally {
    setCarregando(false);
  }
}

  async function handleFiltrar(event) {
    const valor = event.target.value;
    setFiltroLojaId(valor);
    await carregarItens(valor);
  }

  async function handleImportarPlanilha(event) {
    event.preventDefault();

    try {
      setCarregandoImportacao(true);
      setErro("");
      setMensagem("");

      if (!lojaImportacaoId) {
        throw new Error("Selecione a loja para importação.");
      }

      if (!arquivoImportacao) {
        throw new Error("Selecione um arquivo .xlsx para importar.");
      }

      const response = await importarItensPlanilha(Number(lojaImportacaoId), arquivoImportacao);

      setMensagem(
        `${response.mensagem}. ${response.itensImportados} item(ns) importado(s) e ${response.linhasIgnoradas} linha(s) ignorada(s).`
      );

      setLojaImportacaoId("");
      setArquivoImportacao(null);

      const inputArquivo = document.getElementById("input-importacao-planilha");
      if (inputArquivo) {
        inputArquivo.value = "";
      }

      await carregarItens(filtroLojaId);
    } catch (error) {
      setErro(error.message);
    } finally {
      setCarregandoImportacao(false);
    }
  }

  function limparFormularioItem() {
  setItemEditandoId(null);
  setLojaId("");
  setNomeItem("");
  setCmv("");
  setObservacao("");
}

function handleEditarItem(item) {
  setItemEditandoId(item.id);
  setLojaId(String(item.lojaId));
  setNomeItem(item.nomeItem);
  setCmv(item.cmv != null ? String(item.cmv) : "");
  setObservacao(item.observacao || "");
  setMensagem("");
  setErro("");
}

async function handleDesativarItem(item) {
  const confirmou = window.confirm(
    `Tem certeza que deseja desativar o item "${item.nomeItem}"? Ele deixará de aparecer na calculadora.`
  );

  if (!confirmou) {
    return;
  }

  try {
    setCarregando(true);
    setErro("");
    setMensagem("");

    await desativarItem(item.id);

    if (itemEditandoId === item.id) {
      limparFormularioItem();
    }

    setMensagem("Item desativado com sucesso.");
    await carregarItens(filtroLojaId);
  } catch (error) {
    setErro(error.message);
  } finally {
    setCarregando(false);
  }
}

  return (
    <div style={styles.page}>
      <div style={styles.pageHeader}>
        <h2 style={styles.pageTitle}>Itens</h2>
        <p style={styles.pageSubtitle}>
          Cadastre os itens com seu respectivo CMV e filtre por loja quando necessário.
        </p>
      </div>

      <div style={styles.layout}>
        <div style={styles.leftColumn}>
  <form onSubmit={handleSubmit} style={styles.formCard}>
    <h3 style={styles.cardTitle}>
      {itemEditandoId ? "Editar item" : "Cadastrar item"}
    </h3>

    <div style={styles.field}>
      <label style={styles.label}>Loja</label>
      <select
        value={lojaId}
        onChange={(e) => setLojaId(e.target.value)}
        required
        style={styles.input}
      >
        <option value="">Selecione uma loja</option>
        {lojas.map((loja) => (
          <option key={loja.id} value={loja.id}>
            {loja.nome}
          </option>
        ))}
      </select>
    </div>

    <div style={styles.field}>
      <label style={styles.label}>Nome do item</label>
      <input
        style={styles.input}
        type="text"
        value={nomeItem}
        onChange={(e) => setNomeItem(e.target.value)}
        required
      />
    </div>

    <div style={styles.field}>
      <label style={styles.label}>CMV</label>
      <input
        style={styles.input}
        type="number"
        step="0.01"
        min="0"
        value={cmv}
        onChange={(e) => setCmv(e.target.value)}
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
      {carregando
        ? "Salvando..."
        : itemEditandoId
        ? "Salvar alterações"
        : "Cadastrar item"}
    </button>

    {itemEditandoId && (
      <button
        type="button"
        onClick={limparFormularioItem}
        style={styles.secondaryButton}
      >
        Cancelar edição
      </button>
    )}
  </form>

  <form onSubmit={handleImportarPlanilha} style={styles.formCard}>
    <h3 style={styles.cardTitle}>Importar planilha</h3>

    <p style={styles.helperText}>
      Envie um arquivo .xlsx (Excel) com a primeira aba no formato:
      <strong> A1 = Nome do item</strong> e <strong>B1 = CMV</strong>.
    </p>

    <div style={styles.importActions}>
      <a
        href="/planilha_cmv.xlsx"
        download
        style={styles.downloadButton}
      >
        Baixar modelo de planilha
      </a>
    </div>

    <div style={styles.field}>
      <label style={styles.label}>Loja</label>
      <select
        value={lojaImportacaoId}
        onChange={(e) => setLojaImportacaoId(e.target.value)}
        required
        style={styles.input}
      >
        <option value="">Selecione uma loja</option>
        {lojas.map((loja) => (
          <option key={loja.id} value={loja.id}>
            {loja.nome}
          </option>
        ))}
      </select>
    </div>

    <div style={styles.field}>
      <label style={styles.label}>Arquivo .xlsx</label>
      <input
        id="input-importacao-planilha"
        type="file"
        accept=".xlsx"
        onChange={(e) => setArquivoImportacao(e.target.files?.[0] || null)}
        style={styles.fileInput}
      />
    </div>

    <button
      type="submit"
      disabled={carregandoImportacao}
      style={styles.primaryButton}
    >
      {carregandoImportacao ? "Importando..." : "Importar planilha"}
    </button>
  </form>

  {erro && <p style={styles.erro}>{erro}</p>}
  {mensagem && <p style={styles.sucesso}>{mensagem}</p>}
</div>

        <div style={styles.tableCard}>
          <div style={styles.tableHeader}>
            <h3 style={styles.cardTitle}>Lista de itens</h3>

            <div style={styles.filterBox}>
              <label style={styles.label}>Filtrar por loja</label>
              <select value={filtroLojaId} onChange={handleFiltrar} style={styles.input}>
                <option value="">Todas</option>
                {lojas.map((loja) => (
                  <option key={loja.id} value={loja.id}>
                    {loja.nome}
                  </option>
                ))}
              </select>
            </div>
          </div>

          {itens.length === 0 ? (
            <p style={styles.emptyText}>Nenhum item cadastrado.</p>
          ) : (
            <table style={styles.table}>
              <thead>
                <tr>
                  <th>Loja</th>
                  <th>Item</th>
                  <th>CMV</th>
                  <th>Preço de venda atual</th>
                  <th>Observação</th>
                  <th>Ações</th>
                </tr>
              </thead>
              <tbody>
                {itens.map((item) => (
                  <tr key={item.id}>
                    <td>{item.nomeLoja}</td>
                    <td>{item.nomeItem}</td>
                    <td>{formatarMoeda(item.cmv)}</td>
                    <td>
                      {item.precoVendaAtual != null
                        ? formatarMoeda(item.precoVendaAtual)
                        : "-"}
                    </td>
                    <td>{item.observacao || "-"}</td>
                    <td>
                      <div style={styles.actionButtons}>
                        <button
                          type="button"
                          onClick={() => handleEditarItem(item)}
                          style={styles.editButton}
                        >
                          Editar
                        </button>

                        <button
                          type="button"
                          onClick={() => handleDesativarItem(item)}
                          style={styles.deleteButton}
                        >
                          Desativar
                        </button>
                      </div>
                    </td>
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
    alignItems: "start",
    gap: "16px",
    marginBottom: "16px",
    flexWrap: "wrap",
  },

  filterBox: {
    display: "grid",
    gap: "8px",
    minWidth: "240px",
  },

  cardTitle: {
    margin: 0,
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
  },

  leftColumn: {
    display: "grid",
    gap: "20px",
  },

  helperText: {
    margin: 0,
    color: "#D9D9D9",
    fontSize: "14px",
    lineHeight: "1.5",
  },

  fileInput: {
    backgroundColor: "#21262D",
    border: "1px solid #30363D",
    padding: "12px 14px",
    borderRadius: "10px",
    color: "#F6F8FA",
    outline: "none",
    fontSize: "15px",
  },

  importActions: {
    display: "flex",
    justifyContent: "flex-start",
  },

  downloadButton: {
    display: "inline-flex",
    alignItems: "center",
    justifyContent: "center",
    padding: "10px 14px",
    borderRadius: "10px",
    backgroundColor: "#21262D",
    border: "1px solid #30363D",
    color: "#A9CCE3",
    textDecoration: "none",
    fontSize: "14px",
    fontWeight: "bold",
    cursor: "pointer",
  },

  secondaryButton: {
    backgroundColor: "#21262D",
    color: "#F6F8FA",
    border: "1px solid #30363D",
    padding: "12px 18px",
    borderRadius: "10px",
    fontWeight: "bold",
    cursor: "pointer",
  },

  actionButtons: {
    display: "flex",
    gap: "6px",
    alignItems: "center",
    flexDirection: "column",
  },

  editButton: {
    backgroundColor: "transparent",
    color: "#A9CCE3",
    border: "1px solid #A9CCE3",
    padding: "6px 10px",
    borderRadius: "6px",
    fontWeight: "600",
    fontSize: "12px",
    cursor: "pointer",
    width: "82px",
  },

  deleteButton: {
    backgroundColor: "transparent",
    color: "#E63946",
    border: "1px solid #E63946",
    padding: "6px 10px",
    borderRadius: "6px",
    fontWeight: "600",
    fontSize: "12px",
    cursor: "pointer",
    width: "82px",
  },

  editButtonHover: {
    backgroundColor: "#A9CCE320",
  },

  deleteButtonHover: {
    backgroundColor: "#E6394620",
  },
};
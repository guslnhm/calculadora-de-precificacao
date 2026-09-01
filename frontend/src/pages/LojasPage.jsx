import { useEffect, useState } from "react";

import {
  listarLojas,
  criarLoja,
  atualizarLoja,
  buscarPlataformasLoja,
  atualizarPlataformasLoja,
  desativarLoja,
} from "../services/api";

export default function LojasPage() {
  const [lojas, setLojas] = useState([]);

  const [nome, setNome] = useState("");
  const [observacao, setObservacao] = useState("");

  const [ifood, setIfood] = useState(true);
  const [food99, setFood99] = useState(false);

  const [salvando, setSalvando] = useState(false);

  const [erro, setErro] = useState("");
  const [sucesso, setSucesso] = useState("");

  const [lojaEditando, setLojaEditando] = useState(null);

  const [ifoodEdicao, setIfoodEdicao] = useState(false);
  const [food99Edicao, setFood99Edicao] = useState(false);

  const [salvandoEdicao, setSalvandoEdicao] =
    useState(false);
  
  const [nomeEdicao, setNomeEdicao] = useState("");
  const [observacaoEdicao, setObservacaoEdicao] = useState("");

  useEffect(() => {
    carregarLojas();
  }, []);

  async function carregarLojas() {
    try {
      setErro("");

      const lojasBase = await listarLojas();

      const lojasComPlataformas = await Promise.all(
        lojasBase.map(async (loja) => {
          try {
            const resposta =
              await buscarPlataformasLoja(loja.id);

            return {
              ...loja,
              plataformas: resposta.plataformas || [],
            };
          } catch {
            return {
              ...loja,
              plataformas: [],
            };
          }
        })
      );

      setLojas(lojasComPlataformas);
    } catch (error) {
      setErro(error.message);
    }
  }

  function plataformasSelecionadasCadastro() {
    const plataformas = [];

    if (ifood) {
      plataformas.push("IFOOD");
    }

    if (food99) {
      plataformas.push("FOOD99");
    }

    return plataformas;
  }

  function plataformasSelecionadasEdicao() {
    const plataformas = [];

    if (ifoodEdicao) {
      plataformas.push("IFOOD");
    }

    if (food99Edicao) {
      plataformas.push("FOOD99");
    }

    return plataformas;
  }

  async function handleCadastrar(event) {
    event.preventDefault();

    const plataformas =
      plataformasSelecionadasCadastro();

    if (plataformas.length === 0) {
      setErro(
        "Selecione pelo menos uma plataforma para a loja."
      );
      return;
    }

    try {
      setSalvando(true);
      setErro("");
      setSucesso("");

      const novaLoja = await criarLoja({
        nome,
        observacao,
      });

      await atualizarPlataformasLoja(
        novaLoja.id,
        plataformas
      );

      setNome("");
      setObservacao("");

      /*
       * Mantemos iFood marcado por padrão
       * para o próximo cadastro.
       */
      setIfood(true);
      setFood99(false);

      setSucesso("Loja cadastrada com sucesso.");

      await carregarLojas();
    } catch (error) {
      setErro(error.message);
    } finally {
      setSalvando(false);
    }
  }

  function abrirEdicao(loja) {
    const plataformas = loja.plataformas || [];

    setLojaEditando(loja);

    setNomeEdicao(loja.nome || "");
    setObservacaoEdicao(loja.observacao || "");

    setIfoodEdicao(
      plataformas.includes("IFOOD")
    );

    setFood99Edicao(
      plataformas.includes("FOOD99")
    );

    setErro("");
    setSucesso("");
  }

  function fecharEdicao() {
    setLojaEditando(null);

    setNomeEdicao("");
    setObservacaoEdicao("");

    setIfoodEdicao(false);
    setFood99Edicao(false);
  }

  async function handleSalvarEdicao() {
    if (!lojaEditando) {
      return;
    }

    if (!nomeEdicao.trim()) {
      setErro("Informe o nome da loja.");
      return;
    }

    const plataformas =
      plataformasSelecionadasEdicao();

    if (plataformas.length === 0) {
      setErro(
        "A loja precisa estar vinculada a pelo menos uma plataforma."
      );
      return;
    }

    try {
      setSalvandoEdicao(true);
      setErro("");
      setSucesso("");

      await atualizarLoja(
        lojaEditando.id,
        {
          nome: nomeEdicao.trim(),
          observacao: observacaoEdicao,
        }
      );

      await atualizarPlataformasLoja(
        lojaEditando.id,
        plataformas
      );

      setSucesso(
        `Loja "${nomeEdicao.trim()}" atualizada com sucesso.`
      );

      fecharEdicao();

      await carregarLojas();
    } catch (error) {
      setErro(error.message);
    } finally {
      setSalvandoEdicao(false);
    }
  }

  async function handleDesativar(loja) {
    const confirmou = window.confirm(
      `Tem certeza que deseja desativar a loja "${loja.nome}"?\n\nEla deixará de aparecer nas calculadoras, mas os dados continuarão salvos.`
    );

    if (!confirmou) {
      return;
    }

    try {
      setErro("");
      setSucesso("");

      await desativarLoja(loja.id);

      setSucesso(
        `Loja "${loja.nome}" desativada com sucesso.`
      );

      await carregarLojas();
    } catch (error) {
      setErro(error.message);
    }
  }

  function nomePlataforma(plataforma) {
    if (plataforma === "IFOOD") {
      return "iFood";
    }

    if (plataforma === "FOOD99") {
      return "99Food";
    }

    if (plataforma === "RESTAURANTE") {
      return "Restaurante";
    }

    return plataforma;
  }

  return (
    <div style={styles.page}>
      <div style={styles.pageHeader}>
        <h2 style={styles.pageTitle}>Lojas</h2>

        <p style={styles.pageSubtitle}>
          Cadastre as unidades e defina em quais
          plataformas cada loja opera.
        </p>
      </div>

      <div style={styles.layout}>
        <form
          onSubmit={handleCadastrar}
          style={styles.formCard}
        >
          <h3 style={styles.cardTitle}>
            Cadastrar loja
          </h3>

          <div style={styles.field}>
            <label style={styles.label}>
              Nome da loja
            </label>

            <input
              type="text"
              value={nome}
              onChange={(event) =>
                setNome(event.target.value)
              }
              required
              style={styles.input}
            />
          </div>

          <div style={styles.field}>
            <label style={styles.label}>
              Observação
            </label>

            <textarea
              value={observacao}
              onChange={(event) =>
                setObservacao(event.target.value)
              }
              style={styles.textarea}
            />
          </div>

          <div style={styles.plataformasBox}>
            <strong style={styles.plataformasTitulo}>
              Plataformas
            </strong>

            <label style={styles.checkboxRow}>
              <input
                type="checkbox"
                checked={ifood}
                onChange={(event) =>
                  setIfood(event.target.checked)
                }
              />

              <span>iFood</span>
            </label>

            <label style={styles.checkboxRow}>
              <input
                type="checkbox"
                checked={food99}
                onChange={(event) =>
                  setFood99(event.target.checked)
                }
              />

              <span>99Food</span>
            </label>
          </div>

          <button
            type="submit"
            disabled={salvando}
            style={{
              ...styles.primaryButton,
              ...(salvando
                ? styles.disabledButton
                : {}),
            }}
          >
            {salvando
              ? "Cadastrando..."
              : "Cadastrar loja"}
          </button>
        </form>

        <div style={styles.tableCard}>
          <div style={styles.tableHeader}>
            <h3 style={styles.cardTitle}>
              Lista de lojas
            </h3>

            <span style={styles.badge}>
              {lojas.length} cadastrada(s)
            </span>
          </div>

          {lojas.length === 0 ? (
            <p style={styles.emptyText}>
              Nenhuma loja cadastrada.
            </p>
          ) : (
            <div style={styles.tableWrapper}>
              <table style={styles.table}>
                <thead>
                  <tr>
                    <th style={styles.th}>
                      Loja
                    </th>

                    <th style={styles.th}>
                      Plataformas
                    </th>

                    <th style={styles.th}>
                      Observação
                    </th>

                    <th style={styles.th}>
                      Ações
                    </th>
                  </tr>
                </thead>

                <tbody>
                  {lojas.map((loja) => (
                    <tr key={loja.id}>
                      <td style={styles.td}>
                        <strong>
                          {loja.nome}
                        </strong>
                      </td>

                      <td style={styles.td}>
                        <div
                          style={
                            styles.badgesPlataforma
                          }
                        >
                          {(loja.plataformas || [])
                            .map((plataforma) => (
                              <span
                                key={plataforma}
                                style={
                                  styles.platformBadge
                                }
                              >
                                {nomePlataforma(
                                  plataforma
                                )}
                              </span>
                            ))}

                          {(loja.plataformas || [])
                            .length === 0 && (
                            <span
                              style={
                                styles.semPlataforma
                              }
                            >
                              Sem plataforma
                            </span>
                          )}
                        </div>
                      </td>

                      <td style={styles.td}>
                        {loja.observacao || "-"}
                      </td>

                      <td style={styles.td}>
                        <div
                          style={
                            styles.actionButtons
                          }
                        >
                          <button
                            type="button"
                            onClick={() =>
                              abrirEdicao(loja)
                            }
                            style={
                              styles.editButton
                            }
                          >
                            Editar
                          </button>

                          <button
                            type="button"
                            onClick={() =>
                              handleDesativar(
                                loja
                              )
                            }
                            style={
                              styles.deleteButton
                            }
                          >
                            Desativar
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>

      {erro && (
        <p style={styles.erro}>
          {erro}
        </p>
      )}

      {sucesso && (
        <p style={styles.sucesso}>
          {sucesso}
        </p>
      )}

      {lojaEditando && (
        <div style={styles.modalOverlay}>
          <div style={styles.modal}>
            <h3 style={styles.modalTitle}>
              Editar loja
            </h3>

            <p style={styles.modalSubtitle}>
              Altere os dados e as plataformas da unidade.
            </p>

            <div style={styles.field}>
              <label style={styles.label}>
                Nome da loja
              </label>

              <input
                type="text"
                value={nomeEdicao}
                onChange={(event) =>
                  setNomeEdicao(event.target.value)
                }
                style={styles.input}
              />
            </div>

            <div style={styles.field}>
              <label style={styles.label}>
                Observação
              </label>

              <textarea
                value={observacaoEdicao}
                onChange={(event) =>
                  setObservacaoEdicao(event.target.value)
                }
                style={styles.textarea}
              />
            </div>

            <div style={styles.plataformasBox}>
              <strong style={styles.plataformasTitulo}>
                Plataformas
              </strong>

              <label style={styles.checkboxRow}>
                <input
                  type="checkbox"
                  checked={ifoodEdicao}
                  onChange={(event) =>
                    setIfoodEdicao(event.target.checked)
                  }
                />

                <span>iFood</span>
              </label>

              <label style={styles.checkboxRow}>
                <input
                  type="checkbox"
                  checked={food99Edicao}
                  onChange={(event) =>
                    setFood99Edicao(event.target.checked)
                  }
                />

                <span>99Food</span>
              </label>
            </div>

            <div style={styles.modalActions}>
              <button
                type="button"
                onClick={fecharEdicao}
                disabled={salvandoEdicao}
                style={styles.secondaryButton}
              >
                Cancelar
              </button>

              <button
                type="button"
                onClick={handleSalvarEdicao}
                disabled={salvandoEdicao}
                style={{
                  ...styles.primaryButton,
                  ...(salvandoEdicao
                    ? styles.disabledButton
                    : {}),
                }}
              >
                {salvandoEdicao
                  ? "Salvando..."
                  : "Salvar alterações"}
              </button>
            </div>
          </div>
        </div>
      )}
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
  },

  tableCard: {
    backgroundColor: "#161B22",
    border: "1px solid #26313B",
    borderRadius: "18px",
    padding: "22px",
  },

  tableHeader: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    gap: "12px",
    marginBottom: "16px",
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
    minHeight: "100px",
    resize: "vertical",
  },

  plataformasBox: {
    backgroundColor: "#0D1117",
    border: "1px solid #30363D",
    borderRadius: "12px",
    padding: "14px",
    display: "grid",
    gap: "11px",
  },

  plataformasTitulo: {
    marginBottom: "2px",
  },

  checkboxRow: {
    display: "flex",
    alignItems: "center",
    gap: "9px",
    cursor: "pointer",
    color: "#D9D9D9",
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

  secondaryButton: {
    backgroundColor: "#21262D",
    color: "#F6F8FA",
    border: "1px solid #30363D",
    padding: "10px 16px",
    borderRadius: "8px",
    cursor: "pointer",
  },

  disabledButton: {
    opacity: 0.55,
    cursor: "not-allowed",
  },

  tableWrapper: {
    overflowX: "auto",
  },

  table: {
    width: "100%",
    borderCollapse: "collapse",
  },

  th: {
    textAlign: "left",
    padding: "10px",
    color: "#8B949E",
    borderBottom: "1px solid #30363D",
  },

  td: {
    padding: "12px 10px",
    borderBottom: "1px solid #21262D",
    verticalAlign: "middle",
  },

  badgesPlataforma: {
    display: "flex",
    gap: "6px",
    flexWrap: "wrap",
  },

  platformBadge: {
    backgroundColor: "#21262D",
    border: "1px solid #30363D",
    borderRadius: "999px",
    padding: "4px 9px",
    fontSize: "12px",
    fontWeight: 600,
    color: "#A9CCE3",
  },

  semPlataforma: {
    color: "#8B949E",
    fontSize: "13px",
  },

  actionButtons: {
    display: "flex",
    gap: "6px",
    flexDirection: "column",
    width: "90px",
  },

  editButton: {
    backgroundColor: "transparent",
    color: "#A9CCE3",
    border: "1px solid #A9CCE3",
    padding: "6px 10px",
    borderRadius: "6px",
    cursor: "pointer",
  },

  deleteButton: {
    backgroundColor: "transparent",
    color: "#E63946",
    border: "1px solid #E63946",
    padding: "6px 10px",
    borderRadius: "6px",
    cursor: "pointer",
  },

  erro: {
    color: "#FF7B72",
    fontWeight: "bold",
    marginTop: "16px",
  },

  sucesso: {
    color: "#3FB950",
    fontWeight: "bold",
    marginTop: "16px",
  },

  emptyText: {
    color: "#8B949E",
  },

  modalOverlay: {
    position: "fixed",
    inset: 0,
    backgroundColor: "rgba(0, 0, 0, 0.65)",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    zIndex: 9999,
    padding: "20px",
  },

 modal: {
  width: "100%",
  maxWidth: "500px",
  maxHeight: "90vh",
  overflowY: "auto",
  backgroundColor: "#161B22",
  border: "1px solid #30363D",
  borderRadius: "18px",
  padding: "24px",
  boxShadow: "0 20px 70px rgba(0,0,0,.5)",
},

  modalTitle: {
    margin: "0 0 6px",
  },

  modalSubtitle: {
    margin: "0 0 18px",
    color: "#8B949E",
  },

  modalActions: {
    display: "flex",
    justifyContent: "flex-end",
    gap: "10px",
    marginTop: "18px",
  },
};
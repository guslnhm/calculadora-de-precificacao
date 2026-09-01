import { useEffect, useState } from "react";
import {
  buscarPercentuaisLoja,
  listarItens,
  listarLojas,
  salvarPercentuaisLoja,
  simularPrecificacaoReversa,
} from "../services/api";

function formatarMoeda(valor) {
  return Number(valor || 0).toLocaleString("pt-BR", {
    style: "currency",
    currency: "BRL",
  });
}

function formatarPercentual(valor) {
  return `${Number(valor || 0).toFixed(2)}%`;
}

function InputPercentual({ label, value, onChange }) {
  return (
    <div style={styles.field}>
      <label style={styles.label}>{label}</label>
      <div style={styles.inputPercentualWrapper}>
        <input
          type="number"
          step="0.01"
          min="0"
          value={value}
          onChange={onChange}
          style={styles.inputPercentual}
        />
        <span style={styles.percentualSuffix}>%</span>
      </div>
    </div>
  );
}

export default function SimulacaoReversaPage() {
  const [lojas, setLojas] = useState([]);
  const [itens, setItens] = useState([]);

  const [lojaId, setLojaId] = useState("");
  const [itemId, setItemId] = useState("");
  const [itemSelecionado, setItemSelecionado] = useState(null);

  const [precoVenda, setPrecoVenda] = useState("");
  const [frete, setFrete] = useState("");
  const [imposto, setImposto] = useState("");
  const [taxaFranquia, setTaxaFranquia] = useState("");
  const [custoFixo, setCustoFixo] = useState("");
  const [taxaTransacao, setTaxaTransacao] = useState("");
  const [taxaIfood, setTaxaIfood] = useState("");
  const [taxaRepasse, setTaxaRepasse] = useState("");

  const [resultado, setResultado] = useState(null);
  const [erro, setErro] = useState("");
  const [mensagem, setMensagem] = useState("");
  const [carregando, setCarregando] = useState(false);
  const [salvandoPercentuais, setSalvandoPercentuais] = useState(false);

  const somaPercentuais =
    Number(imposto || 0) +
    Number(taxaFranquia || 0) +
    Number(custoFixo || 0) +
    Number(taxaTransacao || 0) +
    Number(taxaIfood || 0) +
    Number(taxaRepasse || 0);

  const percentualInvalido = somaPercentuais >= 100;

  useEffect(() => {
    carregarLojas();
  }, []);

  async function carregarLojas() {
    try {
      const data = await listarLojas("IFOOD");
      setLojas(data);
    } catch (error) {
      setErro(error.message);
    }
  }

  async function carregarItensPorLoja(idLoja) {
    try {
      setErro("");
      setItens([]);
      setItemId("");
      setItemSelecionado(null);
      setPrecoVenda("");
      setResultado(null);

      if (!idLoja) return;

      const data = await listarItens(idLoja);
      setItens(data);
    } catch (error) {
      setErro(error.message);
    }
  }

  function preencherPercentuais(percentuais) {
    setImposto(percentuais?.imposto ?? "");
    setTaxaFranquia(percentuais?.taxaFranquia ?? "");
    setCustoFixo(percentuais?.custoFixo ?? "");
    setTaxaTransacao(percentuais?.taxaTransacao ?? "");
    setTaxaIfood(percentuais?.taxaIfood ?? "");
    setTaxaRepasse(percentuais?.taxaRepasse ?? "");
  }

  async function carregarPercentuaisDaLoja(idLoja) {
    try {
      if (!idLoja) {
        preencherPercentuais({});
        return;
      }

      const percentuais = await buscarPercentuaisLoja(idLoja);
      preencherPercentuais(percentuais);
    } catch (error) {
      setErro(error.message);
    }
  }

  async function handleChangeLoja(event) {
    const valor = event.target.value;
    setLojaId(valor);
    setMensagem("");
    setErro("");
    setResultado(null);

    await carregarItensPorLoja(valor);
    await carregarPercentuaisDaLoja(valor);
  }

  function handleChangeItem(event) {
    const valor = event.target.value;
    setItemId(valor);
    setResultado(null);
    setMensagem("");
    setErro("");

    const item = itens.find((itemAtual) => String(itemAtual.id) === String(valor));
    setItemSelecionado(item || null);

    if (item?.precoVendaAtual != null) {
      setPrecoVenda(String(item.precoVendaAtual));
    } else {
      setPrecoVenda("");
    }
  }

  async function handleSubmit(event) {
    event.preventDefault();

    if (!itemSelecionado) {
      setErro("Selecione um item para simular.");
      return;
    }

    if (Number(precoVenda || 0) <= 0) {
      setErro("Informe um preço de venda maior que zero.");
      return;
    }

    if (percentualInvalido) {
      setErro("A soma dos percentuais não pode ser maior ou igual a 100%.");
      return;
    }

    try {
      setCarregando(true);
      setErro("");
      setMensagem("");
      setResultado(null);

      const data = await simularPrecificacaoReversa({
        itemId: Number(itemId),
        precoVenda: Number(precoVenda),
        frete: Number(frete || 0),
        imposto: Number(imposto || 0),
        taxaFranquia: Number(taxaFranquia || 0),
        custoFixo: Number(custoFixo || 0),
        taxaTransacao: Number(taxaTransacao || 0),
        taxaIfood: Number(taxaIfood || 0),
        taxaRepasse: Number(taxaRepasse || 0),
      });

      setResultado(data);
      setMensagem("Cálculo reverso realizado com sucesso.");
    } catch (error) {
      setErro(error.message);
    } finally {
      setCarregando(false);
    }
  }

  async function handleSalvarPercentuaisLoja() {
    if (!lojaId) {
      setErro("Selecione uma loja para salvar os percentuais.");
      return;
    }

    if (percentualInvalido) {
      setErro("A soma dos percentuais não pode ser maior ou igual a 100%.");
      return;
    }

    try {
      setSalvandoPercentuais(true);
      setErro("");
      setMensagem("");

      await salvarPercentuaisLoja(Number(lojaId), {
        imposto: Number(imposto || 0),
        taxaFranquia: Number(taxaFranquia || 0),
        custoFixo: Number(custoFixo || 0),
        taxaTransacao: Number(taxaTransacao || 0),
        taxaIfood: Number(taxaIfood || 0),
        taxaRepasse: Number(taxaRepasse || 0),
        lucro: 0,
      });

      setMensagem("Percentuais padrão da loja salvos com sucesso.");
    } catch (error) {
      setErro(error.message);
    } finally {
      setSalvandoPercentuais(false);
    }
  }

  function limparFormulario() {
    setLojaId("");
    setItens([]);
    setItemId("");
    setItemSelecionado(null);
    setPrecoVenda("");
    setFrete("");
    setImposto("");
    setTaxaFranquia("");
    setCustoFixo("");
    setTaxaTransacao("");
    setTaxaIfood("");
    setTaxaRepasse("");
    setResultado(null);
    setErro("");
    setMensagem("");
  }

  return (
    <div style={styles.page}>
      <div style={styles.pageHeader}>
        <h2 style={styles.pageTitle}>Calculadora Reversa</h2>
        <p style={styles.pageSubtitle}>
          Informe o preço de venda atual para descobrir o lucro real do item em R$ e em percentual.
        </p>
      </div>

      <div style={styles.layout}>
        <form onSubmit={handleSubmit} style={styles.formCard}>
          <div style={styles.field}>
            <label style={styles.label}>Loja</label>
            <select value={lojaId} onChange={handleChangeLoja} required style={styles.input}>
              <option value="">Selecione uma loja</option>
              {lojas.map((loja) => (
                <option key={loja.id} value={loja.id}>
                  {loja.nome}
                </option>
              ))}
            </select>
          </div>

          <div style={styles.field}>
            <label style={styles.label}>Item</label>
            <select
              value={itemId}
              onChange={handleChangeItem}
              required
              disabled={!lojaId}
              style={styles.input}
            >
              <option value="">Selecione um item</option>
              {itens.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.nomeItem}
                </option>
              ))}
            </select>
          </div>

          {itemSelecionado && (
            <div style={styles.itemInfoBox}>
              <p style={styles.itemInfoText}>
                <strong>CMV:</strong> {formatarMoeda(itemSelecionado.cmv)}
              </p>
              <p style={styles.itemInfoText}>
                <strong>Observação:</strong> {itemSelecionado.observacao || "-"}
              </p>
              <p style={styles.itemInfoText}>
                <strong>Preço atual cadastrado:</strong>{" "}
                {itemSelecionado.precoVendaAtual != null
                  ? formatarMoeda(itemSelecionado.precoVendaAtual)
                  : "Não informado"}
              </p>
            </div>
          )}

          <div style={styles.grid2}>
            <div style={styles.field}>
              <label style={styles.label}>Preço de venda (sem considerar cupons)</label>
              <input
                type="number"
                step="0.01"
                min="0.01"
                value={precoVenda}
                onChange={(e) => setPrecoVenda(e.target.value)}
                required
                style={styles.input}
              />
            </div>

            <div style={styles.field}>
              <label style={styles.label}>Frete</label>
              <input
                type="number"
                step="0.01"
                min="0"
                value={frete}
                onChange={(e) => setFrete(e.target.value)}
                style={styles.input}
              />
            </div>
          </div>

          <div style={styles.grid2}>
            <InputPercentual label="Imposto" value={imposto} onChange={(e) => setImposto(e.target.value)} />
            <InputPercentual label="Taxas de franquia" value={taxaFranquia} onChange={(e) => setTaxaFranquia(e.target.value)} />
            <InputPercentual label="Custo fixo" value={custoFixo} onChange={(e) => setCustoFixo(e.target.value)} />
            <InputPercentual label="Taxa transação" value={taxaTransacao} onChange={(e) => setTaxaTransacao(e.target.value)} />
            <InputPercentual label="Taxa iFood" value={taxaIfood} onChange={(e) => setTaxaIfood(e.target.value)} />
            <InputPercentual label="Taxa antecipação" value={taxaRepasse} onChange={(e) => setTaxaRepasse(e.target.value)} />
          </div>

          <div style={styles.percentuaisBox}>
            <p style={styles.percentuaisBoxText}>
              Salve estes percentuais como padrão da loja selecionada. Eles também serão carregados na calculadora normal.
            </p>

            <button
              type="button"
              onClick={handleSalvarPercentuaisLoja}
              disabled={!lojaId || salvandoPercentuais || percentualInvalido}
              style={{
                ...styles.secondaryButton,
                ...((!lojaId || salvandoPercentuais || percentualInvalido) ? styles.buttonDisabled : {}),
              }}
            >
              {salvandoPercentuais ? "Salvando percentuais..." : "Salvar percentuais da loja"}
            </button>
          </div>

          <div
            style={{
              ...styles.resumoPercentuais,
              ...(percentualInvalido ? styles.resumoPercentuaisErro : {}),
            }}
          >
            <strong>Soma dos percentuais:</strong> {somaPercentuais.toFixed(2)}%
            {percentualInvalido && (
              <div style={{ marginTop: "6px" }}>
                A soma não pode ser maior ou igual a 100%.
              </div>
            )}
          </div>

          <div style={styles.actions}>
            <button type="button" onClick={limparFormulario} style={styles.secondaryButton}>
              Limpar
            </button>

            <button
              type="submit"
              disabled={carregando || percentualInvalido}
              style={{
                ...styles.primaryButton,
                ...((carregando || percentualInvalido) ? styles.buttonDisabled : {}),
              }}
            >
              {carregando ? "Calculando..." : "Calcular lucro"}
            </button>
          </div>

          {erro && <p style={styles.erro}>{erro}</p>}
          {mensagem && <p style={styles.sucesso}>{mensagem}</p>}
        </form>

        <div style={styles.resultCard}>
          {!resultado ? (
            <div style={styles.resultPlaceholder}>
              <h3 style={styles.resultTitle}>Resultado</h3>
              <p style={styles.resultPlaceholderText}>
                Selecione um item e informe o preço de venda para visualizar o lucro real.
              </p>
            </div>
          ) : (
            <>
              <h3 style={styles.resultTitle}>Resultado</h3>

              <div style={styles.infoSection}>
                <p><strong>Loja:</strong> {resultado.nomeLoja}</p>
                <p><strong>Item:</strong> {resultado.nomeItem}</p>
                {/*<p><strong>CMV:</strong> {formatarMoeda(resultado.cmv)}</p>
                <p><strong>Frete:</strong> {formatarMoeda(resultado.frete)}</p>
                <p><strong>CF:</strong> {formatarMoeda(resultado.cf)}</p>*/}
                <p><strong>Preço de venda:</strong> {formatarMoeda(resultado.precoVenda)}</p>
                {/*<p><strong>Percentual total:</strong> {formatarPercentual(resultado.percentualTotal)}</p>
                <p><strong>Custo dos percentuais:</strong> {formatarMoeda(resultado.custoPercentualReais)}</p>*/}
              </div>

              <div
                style={{
                  ...styles.highlightCard,
                  ...(Number(resultado.lucroReais) < 0 ? styles.highlightCardNegative : {}),
                }}
              >
                <p style={styles.highlightLabel}>Margem de lucro estimada</p>
                <p
                  style={{
                    ...styles.highlightValue,
                    ...(Number(resultado.lucroPercentual) < 0 ? styles.highlightValueNegative : {}),
                  }}
                >
                  {formatarPercentual(resultado.lucroPercentual)}
                </p>
                <p style={styles.highlightText}>
                  Lucro estimado em reais: <strong>{formatarMoeda(resultado.lucroReais)}</strong>
                </p>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

const styles = {
  page: {
    color: "#F6F8FA",
    paddingBottom: "8px",
  },

  pageHeader: {
    marginBottom: "24px",
  },

  pageTitle: {
    color: "#F6F8FA",
    marginBottom: "8px",
  },

  pageSubtitle: {
    color: "#D9D9D9",
    margin: 0,
  },

  layout: {
    display: "grid",
    gridTemplateColumns: "1fr 1fr",
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

  resultCard: {
    backgroundColor: "#161B22",
    border: "1px solid #26313B",
    borderRadius: "18px",
    padding: "22px",
    minHeight: "100%",
    boxShadow: "0 14px 40px rgba(0,0,0,0.25)",
  },

  resultTitle: {
    marginTop: 0,
    marginBottom: "18px",
    color: "#F6F8FA",
  },

  resultPlaceholder: {
    display: "grid",
    gap: "10px",
  },

  resultPlaceholderText: {
    color: "#D9D9D9",
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

  grid2: {
    display: "grid",
    gridTemplateColumns: "repeat(2, minmax(0, 1fr))",
    gap: "12px",
  },

  inputPercentualWrapper: {
    display: "grid",
    gridTemplateColumns: "1fr 52px",
    alignItems: "stretch",
    backgroundColor: "#21262D",
    border: "1px solid #30363D",
    borderRadius: "10px",
    overflow: "hidden",
    minWidth: 0,
  },

  inputPercentual: {
    border: "none",
    outline: "none",
    width: "100%",
    minWidth: 0,
    padding: "12px 14px",
    backgroundColor: "transparent",
    color: "#F6F8FA",
    fontSize: "15px",
  },

  percentualSuffix: {
    backgroundColor: "#1B222B",
    color: "#A9CCE3",
    fontWeight: "bold",
    borderLeft: "1px solid #30363D",
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    minWidth: "52px",
  },

  itemInfoBox: {
    backgroundColor: "#1B222B",
    border: "1px solid #30363D",
    borderRadius: "12px",
    padding: "14px",
    display: "grid",
    gap: "8px",
  },

  itemInfoText: {
    margin: 0,
    color: "#D9D9D9",
    lineHeight: "1.5",
  },

  percentuaisBox: {
    backgroundColor: "#1B222B",
    border: "1px solid #30363D",
    borderRadius: "12px",
    padding: "14px",
    display: "grid",
    gap: "12px",
  },

  percentuaisBoxText: {
    margin: 0,
    color: "#D9D9D9",
    lineHeight: "1.5",
  },

  resumoPercentuais: {
    backgroundColor: "#1B222B",
    color: "#F6F8FA",
    padding: "12px 14px",
    borderRadius: "10px",
    border: "1px solid #26313B",
    fontWeight: "bold",
  },

  resumoPercentuaisErro: {
    backgroundColor: "rgba(230, 57, 70, 0.12)",
    color: "#F6F8FA",
    border: "1px solid rgba(230, 57, 70, 0.35)",
  },

  actions: {
    display: "flex",
    gap: "12px",
    justifyContent: "space-between",
    flexWrap: "wrap",
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
    padding: "12px 18px",
    borderRadius: "10px",
    cursor: "pointer",
  },

  buttonDisabled: {
    opacity: 0.6,
    cursor: "not-allowed",
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

  infoSection: {
    display: "grid",
    gap: "8px",
    color: "#D9D9D9",
    marginBottom: "20px",
  },

  highlightCard: {
    backgroundColor: "rgba(169, 204, 227, 0.10)",
    border: "1px solid rgba(169, 204, 227, 0.25)",
    borderRadius: "16px",
    padding: "20px",
    marginBottom: "18px",
  },

  highlightCardNegative: {
    backgroundColor: "rgba(230, 57, 70, 0.10)",
    border: "1px solid rgba(230, 57, 70, 0.35)",
  },

  highlightLabel: {
    margin: 0,
    color: "#A9CCE3",
    fontWeight: "bold",
  },

  highlightValue: {
    margin: "10px 0",
    fontSize: "38px",
    fontWeight: "bold",
    color: "#F6F8FA",
    lineHeight: 1,
  },

  highlightValueNegative: {
    color: "#E63946",
  },

  highlightText: {
    margin: 0,
    color: "#D9D9D9",
    lineHeight: "1.5",
  },

  resultExplanationBox: {
    marginTop: "18px",
    padding: "16px",
    borderRadius: "14px",
    backgroundColor: "#1B222B",
    border: "1px solid #30363D",
  },

  resultExplanationText: {
    margin: 0,
    color: "#D9D9D9",
    lineHeight: "1.5",
  },
};

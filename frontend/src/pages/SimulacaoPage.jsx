import { useEffect, useState } from "react";
import {
  buscarPercentuaisLoja,
  listarItens,
  listarLojas,
  salvarPercentuaisLoja,
  salvarPrecoVendaItem,
  simularPrecificacao,
} from "../services/api";

function formatarMoeda(valor) {
  return Number(valor).toLocaleString("pt-BR", {
    style: "currency",
    currency: "BRL",
  });
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
          //required
          style={styles.inputPercentual}
        />
        <span style={styles.percentualSuffix}>%</span>
      </div>
    </div>
  );
}

export default function SimulacaoPage() {
  const [lojas, setLojas] = useState([]);
  const [itens, setItens] = useState([]);
  const [lojaId, setLojaId] = useState("");
  const [itemId, setItemId] = useState("");
  const [frete, setFrete] = useState("");
  const [imposto, setImposto] = useState("");
  const [custoFixo, setCustoFixo] = useState("");
  const [taxaTransacao, setTaxaTransacao] = useState("");
  const [taxaIfood, setTaxaIfood] = useState("");
  const [taxaRepasse, setTaxaRepasse] = useState("");
  const [lucro, setLucro] = useState("");
  const [resultado, setResultado] = useState(null);
  const [erro, setErro] = useState("");
  const [mensagem, setMensagem] = useState("");
  const [carregando, setCarregando] = useState(false);
  const [precoSelecionado, setPrecoSelecionado] = useState("");
  const [salvandoPreco, setSalvandoPreco] = useState(false);
  const [salvandoPercentuais, setSalvandoPercentuais] = useState(false);
  const [taxaFranquia, setTaxaFranquia] = useState("");

  const somaPercentuais =
    Number(imposto || 0) +
    Number(taxaFranquia || 0) +
    Number(custoFixo || 0) +
    Number(taxaTransacao || 0) +
    Number(taxaIfood || 0) +
    Number(taxaRepasse || 0) +
    Number(lucro || 0);

  const percentualInvalido = somaPercentuais >= 100;

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

      if (!idLoja) return;

      const data = await listarItens(idLoja);
      setItens(data);
    } catch (error) {
      setErro(error.message);
    }
  }

  useEffect(() => {
    carregarLojas();
  }, []);

  async function handleChangeLoja(event) {
    const valor = event.target.value;
    setLojaId(valor);
    setResultado(null);
    setPrecoSelecionado("");
    setMensagem("");
    setErro("");

    await carregarItensPorLoja(valor);
    await carregarPercentuaisDaLoja(valor);
  }

  async function handleSubmit(event) {
    event.preventDefault();

    try {
      setCarregando(true);
      setErro("");
      setMensagem("");
      setResultado(null);
      setPrecoSelecionado("");

      const data = await simularPrecificacao({
        itemId: Number(itemId),
        frete: Number(frete),
        imposto: Number(imposto),
        taxaFranquia: Number(taxaFranquia || 0),
        custoFixo: Number(custoFixo),
        taxaTransacao: Number(taxaTransacao),
        taxaIfood: Number(taxaIfood),
        taxaRepasse: Number(taxaRepasse),
        lucro: Number(lucro),
      });

      setResultado(data);
      setMensagem("Cálculo realizado com sucesso.");
    } catch (error) {
      setErro(error.message);
    } finally {
      setCarregando(false);
    }
  }

  function obterValorPrecoSelecionado() {
    if (!resultado || !precoSelecionado) return null;

    if (precoSelecionado === "base") return resultado.valorBase;
    if (precoSelecionado === "cupom5") return resultado.valorCupom5;
    if (precoSelecionado === "cupom8") return resultado.valorCupom8;
    if (precoSelecionado === "cupom10") return resultado.valorCupom10;

    return null;
  }

  async function handleSalvarPreco() {
    const valorSelecionado = obterValorPrecoSelecionado();

    if (!resultado || !itemId || valorSelecionado == null) {
      setErro("Selecione um preço para salvar.");
      return;
    }

    try {
      setSalvandoPreco(true);
      setErro("");
      setMensagem("");

      await salvarPrecoVendaItem(Number(itemId), {
        precoVendaAtual: Number(valorSelecionado),
      });

      setMensagem("Preço de venda salvo com sucesso.");
    } catch (error) {
      setErro(error.message);
    } finally {
      setSalvandoPreco(false);
    }
  }

  function limparFormulario() {
    setLojaId("");
    setItens([]);
    setItemId("");
    setFrete("");
    setImposto("");
    setTaxaFranquia("");
    setCustoFixo("");
    setTaxaTransacao("");
    setTaxaIfood("");
    setTaxaRepasse("");
    setLucro("");
    setResultado(null);
    setErro("");
    setMensagem("");
    setPrecoSelecionado("");
  }

  function preencherPercentuais(percentuais) {
    setImposto(percentuais?.imposto ?? "");
    setTaxaFranquia(percentuais?.taxaFranquia ?? "");
    setCustoFixo(percentuais?.custoFixo ?? "");
    setTaxaTransacao(percentuais?.taxaTransacao ?? "");
    setTaxaIfood(percentuais?.taxaIfood ?? "");
    setTaxaRepasse(percentuais?.taxaRepasse ?? "");
    setLucro(percentuais?.lucro ?? "");
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
        lucro: Number(lucro || 0),
      });

      setMensagem("Percentuais padrão da loja salvos com sucesso.");
    } catch (error) {
      setErro(error.message);
    } finally {
      setSalvandoPercentuais(false);
    }
  }

  return (
    <div style={styles.page}>
      <div style={styles.pageHeader}>
        <h2 style={styles.pageTitle}>Calculadora de Precificação</h2>
        <p style={styles.pageSubtitle}>
          Escolha a loja, selecione o item e informe os percentuais para gerar os preços sugeridos.
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
              onChange={(e) => setItemId(e.target.value)}
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

          <div style={styles.field}>
            <label style={styles.label}>Frete</label>
            <input
              type="number"
              step="0.01"
              min="0"
              value={frete}
              onChange={(e) => setFrete(e.target.value)}
              required
              style={styles.input}
            />
          </div>

          <div style={styles.grid2}>
            <InputPercentual label="Imposto" value={imposto} onChange={(e) => setImposto(e.target.value)} />
            <InputPercentual label="Taxas de franquia" value={taxaFranquia} onChange={(e) => setTaxaFranquia(e.target.value)} />
            <InputPercentual label="Custo fixo" value={custoFixo} onChange={(e) => setCustoFixo(e.target.value)} />
            <InputPercentual label="Taxa transação" value={taxaTransacao} onChange={(e) => setTaxaTransacao(e.target.value)} />
            <InputPercentual label="Taxa iFood" value={taxaIfood} onChange={(e) => setTaxaIfood(e.target.value)} />
            <InputPercentual label="Taxa antecipação" value={taxaRepasse} onChange={(e) => setTaxaRepasse(e.target.value)} />
            <InputPercentual label="Lucro" value={lucro} onChange={(e) => setLucro(e.target.value)} />
          </div>

          <div style={styles.percentuaisBox}>
            <p style={styles.percentuaisBoxText}>
              Salve estes percentuais como padrão da loja selecionada para preenchimento automático nas próximas simulações.
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

          {/*<div
            style={{
              ...styles.resumoPercentuais,
              ...(percentualInvalido ? styles.resumoPercentuaisErro : {}),
            }}
          >
            <strong>Soma total dos percentuais:</strong> {somaPercentuais.toFixed(2)}%
            {percentualInvalido && (
              <div style={{ marginTop: "6px" }}>
                A soma não pode ser maior ou igual a 100%.
              </div>
            )}
          </div>*/}

          <div style={styles.actions}>
            <button type="button" onClick={limparFormulario} style={styles.secondaryButton}>
              Limpar
            </button>

            <button type="submit" disabled={carregando || percentualInvalido} style={styles.primaryButton}>
              {carregando ? "Calculando..." : "Calcular"}
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
                Preencha os dados ao lado para visualizar a precificação.
              </p>
            </div>
          ) : (
            <>
              <h3 style={styles.resultTitle}>Resultado</h3>

              <div style={styles.infoSection}>
                <p><strong>Loja:</strong> {resultado.nomeLoja}</p>
                <p><strong>Item:</strong> {resultado.nomeItem}</p>
                <p><strong>CMV:</strong> {formatarMoeda(resultado.cmv)}</p>
                <p><strong>Frete:</strong> {formatarMoeda(resultado.frete)}</p>
                <p><strong>CMV + Frete:</strong> {formatarMoeda(resultado.cmvMaisFrete)}</p>
                <p><strong>Percentual total:</strong> {Number(resultado.percentualTotal).toFixed(2)}%</p>
              </div>

              <div
                style={{
                  ...styles.selectableCard,
                  ...(precoSelecionado === "base" ? styles.selectableCardSelected : {}),
                }}
                onClick={() => setPrecoSelecionado("base")}
              >
                <div style={styles.radioRow}>
                  <input
                    type="radio"
                    name="precoSelecionado"
                    checked={precoSelecionado === "base"}
                    onChange={() => setPrecoSelecionado("base")}
                  />
                  <span style={styles.radioLabel}>Salvar este preço</span>
                </div>

                <div style={styles.highlightCardInner}>
                  <p style={styles.highlightLabel}>Preço base sugerido</p>
                  <p style={styles.highlightValue}>{formatarMoeda(resultado.valorBase)}</p>
                  <p style={styles.highlightText}>
                    Valor principal calculado a partir do CMV, frete e percentuais informados.
                  </p>
                </div>
              </div>

              <div style={styles.cuponsContainer}>
                <div
                  style={{
                    ...styles.cardCupom,
                    ...(precoSelecionado === "cupom5" ? styles.cardCupomSelected : {}),
                  }}
                  onClick={() => setPrecoSelecionado("cupom5")}
                >
                  <div style={styles.radioRow}>
                    <input
                      type="radio"
                      name="precoSelecionado"
                      checked={precoSelecionado === "cupom5"}
                      onChange={() => setPrecoSelecionado("cupom5")}
                    />
                    <span style={styles.radioLabel}>Salvar</span>
                  </div>
                  <p style={styles.cupomTitulo}>Cupom de R$ 5</p>
                  <p style={styles.cupomValor}>{formatarMoeda(resultado.valorCupom5)}</p>
                </div>

                <div
                  style={{
                    ...styles.cardCupom,
                    ...(precoSelecionado === "cupom8" ? styles.cardCupomSelected : {}),
                  }}
                  onClick={() => setPrecoSelecionado("cupom8")}
                >
                  <div style={styles.radioRow}>
                    <input
                      type="radio"
                      name="precoSelecionado"
                      checked={precoSelecionado === "cupom8"}
                      onChange={() => setPrecoSelecionado("cupom8")}
                    />
                    <span style={styles.radioLabel}>Salvar</span>
                  </div>
                  <p style={styles.cupomTitulo}>Cupom de R$ 8</p>
                  <p style={styles.cupomValor}>{formatarMoeda(resultado.valorCupom8)}</p>
                </div>

                <div
                  style={{
                    ...styles.cardCupom,
                    ...(precoSelecionado === "cupom10" ? styles.cardCupomSelected : {}),
                  }}
                  onClick={() => setPrecoSelecionado("cupom10")}
                >
                  <div style={styles.radioRow}>
                    <input
                      type="radio"
                      name="precoSelecionado"
                      checked={precoSelecionado === "cupom10"}
                      onChange={() => setPrecoSelecionado("cupom10")}
                    />
                    <span style={styles.radioLabel}>Salvar</span>
                  </div>
                  <p style={styles.cupomTitulo}>Cupom de R$ 10</p>
                  <p style={styles.cupomValor}>{formatarMoeda(resultado.valorCupom10)}</p>
                </div>
              </div>

              <div style={styles.savePriceBox}>
                <p style={styles.savePriceText}>
                  {precoSelecionado
                    ? `Preço selecionado para salvar: ${formatarMoeda(obterValorPrecoSelecionado())}`
                    : "Selecione um dos preços acima para salvar no item."}
                </p>

                <button
                  type="button"
                  onClick={handleSalvarPreco}
                  disabled={!precoSelecionado || salvandoPreco}
                  style={{
                    ...styles.primaryButton,
                    ...((!precoSelecionado || salvandoPreco) ? styles.buttonDisabled : {}),
                  }}
                >
                  {salvandoPreco ? "Salvando preço..." : "Salvar preço selecionado"}
                </button>
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
    minWidth:0,
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

  highlightText: {
    margin: 0,
    color: "#D9D9D9",
    lineHeight: "1.5",
  },

  cuponsContainer: {
    display: "grid",
    gridTemplateColumns: "repeat(3, 1fr)",
    gap: "12px",
  },

  cardCupom: {
    backgroundColor: "#21262D",
    border: "1px solid #30363D",
    padding: "16px",
    borderRadius: "12px",
  },

  cupomTitulo: {
    margin: 0,
    color: "#D9D9D9",
    fontWeight: "bold",
    marginBottom: "8px",
  },

  cupomValor: {
    margin: 0,
    fontSize: "24px",
    fontWeight: "bold",
    color: "#F6F8FA",
  },

  selectableCard: {
    borderRadius: "16px",
    marginBottom: "18px",
    border: "1px solid rgba(169, 204, 227, 0.18)",
    backgroundColor: "rgba(169, 204, 227, 0.06)",
    padding: "14px",
    cursor: "pointer",
  },

  selectableCardSelected: {
    border: "1px solid rgba(169, 204, 227, 0.55)",
    boxShadow: "0 0 0 1px rgba(169, 204, 227, 0.18) inset",
  },

  highlightCardInner: {
    backgroundColor: "rgba(169, 204, 227, 0.10)",
    border: "1px solid rgba(169, 204, 227, 0.25)",
    borderRadius: "16px",
    padding: "20px",
  },

  radioRow: {
    display: "flex",
    alignItems: "center",
    gap: "8px",
    marginBottom: "12px",
    color: "#D9D9D9",
  },

  radioLabel: {
    fontSize: "14px",
    color: "#D9D9D9",
  },

  cardCupomSelected: {
    border: "1px solid rgba(169, 204, 227, 0.55)",
    boxShadow: "0 0 0 1px rgba(169, 204, 227, 0.14) inset",
  },

  savePriceBox: {
    marginTop: "18px",
    padding: "16px",
    borderRadius: "14px",
    backgroundColor: "#1B222B",
    border: "1px solid #30363D",
    display: "grid",
    gap: "12px",
  },

  savePriceText: {
    margin: 0,
    color: "#D9D9D9",
  },

  buttonDisabled: {
    opacity: 0.6,
    cursor: "not-allowed",
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
};
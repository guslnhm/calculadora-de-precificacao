import { useEffect, useState } from "react";
import {
  listarItens,
  listarLojas,
  salvarPrecoVendaItem,
  simularPrecificacao99,
  buscarConfiguracaoPlataforma,
  salvarConfiguracaoPlataforma,
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
          style={styles.inputPercentual}
        />

        <span style={styles.percentualSuffix}>%</span>
      </div>
    </div>
  );
}

const custosLogisticos = {
  ATE_3_KM: 4,
  ATE_5_KM: 6,
  ACIMA_5_KM: 7.5,
};

export default function Simulacao99Page() {
  const [lojas, setLojas] = useState([]);
  const [itens, setItens] = useState([]);

  const [lojaId, setLojaId] = useState("");
  const [itemId, setItemId] = useState("");
  const [faixaDistancia, setFaixaDistancia] = useState("");

  const [taxa99, setTaxa99] = useState("");
  const [imposto, setImposto] = useState("");
  const [custoFixo, setCustoFixo] = useState("");
  const [taxaFranquia, setTaxaFranquia] = useState("");
  const [taxaTransacao, setTaxaTransacao] = useState("");
  const [taxaAntecipacao, setTaxaAntecipacao] = useState("");
  const [lucro, setLucro] = useState("");

  const [resultado, setResultado] = useState(null);
  const [erro, setErro] = useState("");
  const [mensagem, setMensagem] = useState("");
  const [carregando, setCarregando] = useState(false);

  const [precoSelecionado, setPrecoSelecionado] = useState("");
  const [salvandoPreco, setSalvandoPreco] = useState(false);

  const [salvandoParametros, setSalvandoParametros] = useState(false);

  const somaPercentuais =
    Number(taxa99 || 0) +
    Number(imposto || 0) +
    Number(custoFixo || 0) +
    Number(taxaFranquia || 0) +
    Number(taxaTransacao || 0) +
    Number(taxaAntecipacao || 0) +
    Number(lucro || 0);

  const percentualInvalido = somaPercentuais >= 100;

  const custoLogisticoSelecionado =
    custosLogisticos[faixaDistancia] ?? 0;

  useEffect(() => {
    carregarLojas();
  }, []);

  async function carregarLojas() {
    try {
      const data = await listarLojas("FOOD99");
      setLojas(data);
    } catch (error) {
      setErro(error.message);
    }
  }

  async function carregarConfiguracao99(idLoja) {
    if (!idLoja) return;

    try {
      const configuracao = await buscarConfiguracaoPlataforma(
        idLoja,
        "FOOD99"
      );

      setTaxa99(
        String(configuracao.percentualTaxaPlataforma ?? 0)
      );

      setImposto(
        String(configuracao.percentualImposto ?? 0)
      );

      setCustoFixo(
        String(configuracao.percentualCustoFixo ?? 0)
      );

      setTaxaFranquia(
        String(configuracao.percentualTaxaFranquia ?? 0)
      );

      setTaxaTransacao(
        String(configuracao.percentualTaxaTransacao ?? 0)
      );

      setTaxaAntecipacao(
        String(configuracao.percentualTaxaAntecipacao ?? 0)
      );

      setLucro(
        String(configuracao.percentualLucro ?? 0)
      );
    } catch (error) {
      setErro(error.message);
    }
  }

  async function carregarItensPorLoja(idLoja) {
    try {
      setItens([]);
      setItemId("");

      if (!idLoja) return;

      const data = await listarItens(idLoja);
      setItens(data);
    } catch (error) {
      setErro(error.message);
    }
  }

  async function handleSalvarParametros() {
    if (!lojaId) {
      setErro("Selecione uma loja antes de salvar os parâmetros.");
      return;
    }

    if (percentualInvalido) {
      setErro(
        "A soma dos percentuais não pode ser maior ou igual a 100%."
      );
      return;
    }

    try {
      setSalvandoParametros(true);
      setErro("");
      setMensagem("");

      await salvarConfiguracaoPlataforma(Number(lojaId), {
        plataforma: "FOOD99",

        percentualTaxaPlataforma: Number(taxa99 || 0),
        percentualImposto: Number(imposto || 0),
        percentualCustoFixo: Number(custoFixo || 0),
        percentualTaxaFranquia: Number(taxaFranquia || 0),
        percentualTaxaTransacao: Number(taxaTransacao || 0),
        percentualTaxaAntecipacao: Number(taxaAntecipacao || 0),
        percentualLucro: Number(lucro || 0),
      });

      setMensagem("Parâmetros da 99Food salvos para esta loja.");
    } catch (error) {
      setErro(error.message);
    } finally {
      setSalvandoParametros(false);
    }
  }

  async function handleChangeLoja(event) {
    const valor = event.target.value;

    setLojaId(valor);
    setResultado(null);
    setPrecoSelecionado("");
    setMensagem("");
    setErro("");

    if (!valor) {
      setItens([]);
      setItemId("");

      setTaxa99("");
      setImposto("");
      setCustoFixo("");
      setTaxaFranquia("");
      setTaxaTransacao("");
      setTaxaAntecipacao("");
      setLucro("");

      return;
    }

    await Promise.all([
      carregarItensPorLoja(valor),
      carregarConfiguracao99(valor),
    ]);
  }

  async function handleSubmit(event) {
    event.preventDefault();

    if (percentualInvalido) {
      setErro(
        "A soma dos percentuais não pode ser maior ou igual a 100%."
      );
      return;
    }

    try {
      setCarregando(true);
      setErro("");
      setMensagem("");
      setResultado(null);
      setPrecoSelecionado("");

      const data = await simularPrecificacao99({
        itemId: Number(itemId),
        faixaDistancia,
        taxa99: Number(taxa99 || 0),
        imposto: Number(imposto || 0),
        custoFixo: Number(custoFixo || 0),
        taxaFranquia: Number(taxaFranquia || 0),
        taxaTransacao: Number(taxaTransacao || 0),
        taxaAntecipacao: Number(taxaAntecipacao || 0),
        lucro: Number(lucro || 0),
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

    const precos = {
      prato: resultado.valorPrato,
      freteGratis: resultado.valorFreteGratis,
      off20: resultado.valor20Off,
      off30: resultado.valor30Off,
      off40: resultado.valor40Off,
      off50: resultado.valor50Off,
      off60: resultado.valor60Off,

      copart30: resultado.valor30OffCoparticipacao,
      copart40: resultado.valor40OffCoparticipacao,
      copart50: resultado.valor50OffCoparticipacao,
    };

    return precos[precoSelecionado];
  }

  async function handleSalvarPreco() {
    const valor = obterValorPrecoSelecionado();

    if (!itemId || valor == null) {
      setErro("Selecione um preço para salvar.");
      return;
    }

    try {
      setSalvandoPreco(true);
      setErro("");
      setMensagem("");

      await salvarPrecoVendaItem(Number(itemId), {
        precoVendaAtual: Number(valor),
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
    setFaixaDistancia("");

    setTaxa99("");
    setImposto("");
    setCustoFixo("");
    setTaxaFranquia("");
    setTaxaTransacao("");
    setTaxaAntecipacao("");
    setLucro("");

    setResultado(null);
    setPrecoSelecionado("");
    setErro("");
    setMensagem("");
  }

  function CardPreco({ id, titulo, valor, destaque = false }) {
    const selecionado = precoSelecionado === id;

    return (
      <div
        onClick={() => setPrecoSelecionado(id)}
        style={{
          ...styles.priceCard,
          ...(destaque ? styles.priceCardHighlight : {}),
          ...(selecionado ? styles.priceCardSelected : {}),
        }}
      >
        <div style={styles.radioRow}>
          <input
            type="radio"
            name="preco99"
            checked={selecionado}
            onChange={() => setPrecoSelecionado(id)}
          />

          <span style={styles.radioText}>Salvar este preço</span>
        </div>

        <p style={styles.priceTitle}>{titulo}</p>
        <p style={styles.priceValue}>{formatarMoeda(valor)}</p>
      </div>
    );
  }

  function CardCoparticipacao() {
    const opcoes = [
      {
        id: "copart30",
        titulo: "30% OFF",
        participacao: "15% pela 99",
        valor: resultado.valor30OffCoparticipacao,
      },
      {
        id: "copart40",
        titulo: "40% OFF",
        participacao: "20% pela 99",
        valor: resultado.valor40OffCoparticipacao,
      },
      {
        id: "copart50",
        titulo: "50% OFF",
        participacao: "25% pela 99",
        valor: resultado.valor50OffCoparticipacao,
      },
    ];

    return (
      <div style={styles.copartCard}>
        <div style={styles.copartHeader}>
          <strong>Coparticipação 99Food</strong>
        </div>

        {opcoes.map((opcao) => {
          const selecionado = precoSelecionado === opcao.id;

          return (
            <label
              key={opcao.id}
              style={{
                ...styles.copartRow,
                ...(selecionado ? styles.copartRowSelected : {}),
              }}
            >
              <input
                type="radio"
                name="preco99"
                checked={selecionado}
                onChange={() => setPrecoSelecionado(opcao.id)}
              />

              <div style={styles.copartContent}>
                <div>
                  <strong>{opcao.titulo}</strong>

                  <span style={styles.copartParticipation}>
                    {opcao.participacao}
                  </span>
                </div>

                <strong style={styles.copartValue}>
                  {formatarMoeda(opcao.valor)}
                </strong>
              </div>
            </label>
          );
        })}
      </div>
    );
  }

  return (
    <div style={styles.page}>
      <div style={styles.pageHeader}>
        <h2 style={styles.pageTitle}>
          Calculadora 99Food
        </h2>

        <p style={styles.pageSubtitle}>
          Calcule o preço de venda considerando CMV, distância,
          custo logístico e promoções da 99Food.
        </p>
      </div>

      <div style={styles.layout}>
        <form onSubmit={handleSubmit} style={styles.formCard}>
          <div style={styles.field}>
            <label style={styles.label}>Loja</label>

            <select
              value={lojaId}
              onChange={handleChangeLoja}
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
            <label style={styles.label}>Produto</label>

            <select
              value={itemId}
              onChange={(e) => setItemId(e.target.value)}
              required
              disabled={!lojaId}
              style={styles.input}
            >
              <option value="">Selecione um produto</option>

              {itens.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.nomeItem}
                </option>
              ))}
            </select>
          </div>

          <div style={styles.field}>
            <label style={styles.label}>Distância</label>

            <select
              value={faixaDistancia}
              onChange={(e) => {
                setFaixaDistancia(e.target.value);
                setResultado(null);
              }}
              required
              style={styles.input}
            >
              <option value="">Selecione a distância</option>
              <option value="ATE_3_KM">Até 3 km</option>
              <option value="ATE_5_KM">Até 5 km</option>
              <option value="ACIMA_5_KM">Acima de 5 km</option>
            </select>
          </div>

          {faixaDistancia && (
            <div style={styles.logisticaBox}>
              <span>Custo logístico</span>
              <strong>
                {formatarMoeda(custoLogisticoSelecionado)}
              </strong>
            </div>
          )}

          <div style={styles.grid2}>
            <InputPercentual
              label="Taxa 99"
              value={taxa99}
              onChange={(e) => setTaxa99(e.target.value)}
            />

            <InputPercentual
              label="Imposto"
              value={imposto}
              onChange={(e) => setImposto(e.target.value)}
            />

            <InputPercentual
              label="Custo fixo"
              value={custoFixo}
              onChange={(e) => setCustoFixo(e.target.value)}
            />

            <InputPercentual
              label="Taxa franquia"
              value={taxaFranquia}
              onChange={(e) => setTaxaFranquia(e.target.value)}
            />

            <InputPercentual
              label="Taxa transação"
              value={taxaTransacao}
              onChange={(e) => setTaxaTransacao(e.target.value)}
            />

            <InputPercentual
              label="Taxa antecipação"
              value={taxaAntecipacao}
              onChange={(e) =>
                setTaxaAntecipacao(e.target.value)
              }
            />

            <InputPercentual
              label="Lucro"
              value={lucro}
              onChange={(e) => setLucro(e.target.value)}
            />
          </div>

          <div
            style={{
              ...styles.percentualResumo,
              ...(percentualInvalido
                ? styles.percentualResumoErro
                : {}),
            }}
          >
            <span>Soma dos percentuais</span>

            <strong>
              {somaPercentuais.toFixed(2)}%
            </strong>
          </div>

          {percentualInvalido && (
            <p style={styles.erro}>
              A soma dos percentuais deve ser menor que 100%.
            </p>
          )}

          <button
            type="button"
            onClick={handleSalvarParametros}
            disabled={
              !lojaId ||
              salvandoParametros ||
              percentualInvalido
            }
            style={{
              ...styles.saveParametersButton,
              ...(
                !lojaId ||
                salvandoParametros ||
                percentualInvalido
                  ? styles.disabledButton
                  : {}
              ),
            }}
          >
            {salvandoParametros
              ? "Salvando parâmetros..."
              : "Salvar parâmetros da loja"}
          </button>

          <div style={styles.actions}>
            <button
              type="button"
              onClick={limparFormulario}
              style={styles.secondaryButton}
            >
              Limpar
            </button>

            <button
              type="submit"
              disabled={carregando || percentualInvalido}
              style={{
                ...styles.primaryButton,
                ...(carregando || percentualInvalido
                  ? styles.disabledButton
                  : {}),
              }}
            >
              {carregando ? "Calculando..." : "Calcular"}
            </button>
          </div>

          {erro && <p style={styles.erro}>{erro}</p>}
          {mensagem && <p style={styles.sucesso}>{mensagem}</p>}
        </form>

        <div style={styles.resultCard}>
          {!resultado ? (
            <div>
              <h3 style={styles.resultTitle}>Resultado</h3>

              <p style={styles.placeholder}>
                Selecione a loja, o produto, a distância e informe
                os percentuais para calcular os preços.
              </p>
            </div>
          ) : (
            <>
              <h3 style={styles.resultTitle}>Resultado</h3>

              <div style={styles.infoSection}>
                <p>
                  <strong>Loja:</strong> {resultado.nomeLoja}
                </p>

                <p>
                  <strong>Produto:</strong> {resultado.nomeItem}
                </p>

                <p>
                  <strong>CMV:</strong>{" "}
                  {formatarMoeda(resultado.cmv)}
                </p>

                <p>
                  <strong>Custo logístico:</strong>{" "}
                  {formatarMoeda(resultado.custoLogistico)}
                </p>

                <p>
                  <strong>Percentual total:</strong>{" "}
                  {Number(resultado.percentualTotal).toFixed(2)}%
                </p>
              </div>

              <CardPreco
                id="prato"
                titulo="Valor do prato"
                valor={resultado.valorPrato}
                destaque
              />

              <CardPreco
                id="freteGratis"
                titulo="Com Frete Grátis"
                valor={resultado.valorFreteGratis}
              />

              <div style={styles.promoGrid}>
                <CardPreco
                  id="off20"
                  titulo="20% OFF"
                  valor={resultado.valor20Off}
                />

                <CardPreco
                  id="off30"
                  titulo="30% OFF"
                  valor={resultado.valor30Off}
                />

                <CardPreco
                  id="off40"
                  titulo="40% OFF"
                  valor={resultado.valor40Off}
                />

                <CardPreco
                  id="off50"
                  titulo="50% OFF"
                  valor={resultado.valor50Off}
                />

                <CardPreco
                  id="off60"
                  titulo="60% OFF"
                  valor={resultado.valor60Off}
                />

                <CardCoparticipacao />
              </div>

              <div style={styles.saveBox}>
                <p style={styles.saveText}>
                  {precoSelecionado
                    ? `Preço selecionado: ${formatarMoeda(
                        obterValorPrecoSelecionado()
                      )}`
                    : "Selecione um dos preços acima caso queira salvá-lo no produto."}
                </p>

                <button
                  type="button"
                  onClick={handleSalvarPreco}
                  disabled={!precoSelecionado || salvandoPreco}
                  style={{
                    ...styles.primaryButton,
                    ...(!precoSelecionado || salvandoPreco
                      ? styles.disabledButton
                      : {}),
                  }}
                >
                  {salvandoPreco
                    ? "Salvando..."
                    : "Salvar preço selecionado"}
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
  },

  resultCard: {
    backgroundColor: "#161B22",
    border: "1px solid #26313B",
    borderRadius: "18px",
    padding: "22px",
  },

  field: {
    display: "grid",
    gap: "6px",
  },

  label: {
    fontWeight: 600,
  },

  input: {
    padding: "11px 12px",
    borderRadius: "8px",
    border: "1px solid #30363D",
    backgroundColor: "#0D1117",
    color: "#F6F8FA",
  },

  inputPercentualWrapper: {
    position: "relative",
  },

  inputPercentual: {
    width: "100%",
    boxSizing: "border-box",
    padding: "11px 36px 11px 12px",
    borderRadius: "8px",
    border: "1px solid #30363D",
    backgroundColor: "#0D1117",
    color: "#F6F8FA",
  },

  percentualSuffix: {
    position: "absolute",
    right: "12px",
    top: "50%",
    transform: "translateY(-50%)",
    color: "#8B949E",
  },

  grid2: {
    display: "grid",
    gridTemplateColumns: "1fr 1fr",
    gap: "12px",
  },

  logisticaBox: {
    display: "flex",
    justifyContent: "space-between",
    padding: "12px",
    borderRadius: "10px",
    backgroundColor: "#0D1117",
    border: "1px solid #30363D",
  },

  percentualResumo: {
    display: "flex",
    justifyContent: "space-between",
    padding: "12px",
    borderRadius: "10px",
    backgroundColor: "#0D1117",
    border: "1px solid #30363D",
  },

  percentualResumoErro: {
    border: "1px solid #F85149",
  },

  actions: {
    display: "flex",
    justifyContent: "flex-end",
    gap: "10px",
  },

  primaryButton: {
    border: "none",
    borderRadius: "8px",
    padding: "11px 16px",
    cursor: "pointer",
    fontWeight: 700,
    backgroundColor: "#f7d600",
    color: "#000000",
  },

  secondaryButton: {
    border: "1px solid #30363D",
    borderRadius: "8px",
    padding: "11px 16px",
    cursor: "pointer",
    backgroundColor: "#21262D",
    color: "#F6F8FA",
  },

  disabledButton: {
    opacity: 0.5,
    cursor: "not-allowed",
  },

  resultTitle: {
    marginTop: 0,
  },

  placeholder: {
    color: "#8B949E",
  },

  infoSection: {
    color: "#D9D9D9",
    paddingBottom: "12px",
    borderBottom: "1px solid #30363D",
    marginBottom: "14px",
  },

  priceCard: {
    border: "1px solid #30363D",
    borderRadius: "12px",
    padding: "14px",
    marginBottom: "10px",
    cursor: "pointer",
    backgroundColor: "#0D1117",
  },

  priceCardHighlight: {
    border: "1px solid #2EA043",
  },

  priceCardSelected: {
    outline: "2px solid #58A6FF",
  },

  radioRow: {
    display: "flex",
    alignItems: "center",
    gap: "7px",
    marginBottom: "8px",
  },

  radioText: {
    color: "#8B949E",
    fontSize: "13px",
  },

  priceTitle: {
    margin: "0 0 5px",
    color: "#D9D9D9",
  },

  priceValue: {
    margin: 0,
    fontSize: "24px",
    fontWeight: 700,
    color: "#F6F8FA",
  },

  promoGrid: {
    display: "grid",
    gridTemplateColumns: "1fr 1fr",
    gap: "10px",
  },

  saveBox: {
    borderTop: "1px solid #30363D",
    marginTop: "14px",
    paddingTop: "14px",
  },

  saveText: {
    color: "#D9D9D9",
  },

  erro: {
    color: "#FF7B72",
    margin: 0,
  },

  sucesso: {
    color: "#3FB950",
    margin: 0,
  },

  copartCard: {
    border: "1px solid #30363D",
    borderRadius: "12px",
    backgroundColor: "#0D1117",
    overflow: "hidden",
    marginBottom: "10px",
  },

  copartHeader: {
    padding: "11px 12px",
    borderBottom: "1px solid #30363D",
    color: "#F6F8FA",
    fontSize: "14px",
  },

  copartRow: {
    display: "flex",
    alignItems: "center",
    gap: "8px",
    padding: "9px 10px",
    cursor: "pointer",
    borderBottom: "1px solid #21262D",
  },

  copartRowSelected: {
    outline: "2px solid #58A6FF",
    outlineOffset: "-2px",
  },

  copartContent: {
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    gap: "8px",
    width: "100%",
  },

  copartParticipation: {
    display: "block",
    marginTop: "2px",
    color: "#8B949E",
    fontSize: "11px",
  },

  copartValue: {
    whiteSpace: "nowrap",
    fontSize: "16px",
    color: "#F6F8FA",
  },

  saveParametersButton: {
    width: "100%",
    border: "1px solid #30363D",
    borderRadius: "8px",
    padding: "11px 16px",
    cursor: "pointer",
    fontWeight: 700,
    backgroundColor: "#21262D",
    color: "#F6F8FA",
  },
};
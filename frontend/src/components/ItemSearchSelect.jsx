import { useEffect, useRef, useState } from "react";

function normalizarTexto(texto = "") {
  return texto
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .trim();
}

export default function ItemSearchSelect({
  itens = [],
  value,
  onChange,
  disabled = false,
  placeholder = "Digite para procurar um item...",
}) {
  const [busca, setBusca] = useState("");
  const [aberto, setAberto] = useState(false);

  const containerRef = useRef(null);

  const itemSelecionado = itens.find(
    (item) => String(item.id) === String(value)
  );

  useEffect(() => {
    if (itemSelecionado) {
      setBusca(itemSelecionado.nomeItem);
    }
  }, [itemSelecionado]);

  useEffect(() => {
    function handleClickFora(event) {
      if (
        containerRef.current &&
        !containerRef.current.contains(event.target)
      ) {
        setAberto(false);
      }
    }

    document.addEventListener("mousedown", handleClickFora);

    return () => {
      document.removeEventListener(
        "mousedown",
        handleClickFora
      );
    };
  }, []);

  const termo = normalizarTexto(busca);

  const itensFiltrados = itens.filter((item) =>
    normalizarTexto(item.nomeItem).includes(termo)
  );

  function handleBusca(event) {
    const texto = event.target.value;

    setBusca(texto);
    setAberto(true);

    if (
      itemSelecionado &&
      texto !== itemSelecionado.nomeItem
    ) {
      onChange("");
    }
  }

  function selecionarItem(item) {
    onChange(String(item.id));
    setBusca(item.nomeItem);
    setAberto(false);
  }

  return (
    <div
      ref={containerRef}
      style={styles.container}
    >
      <input
        type="text"
        value={busca}
        onChange={handleBusca}
        onFocus={() => {
          if (!disabled) {
            setAberto(true);
          }
        }}
        disabled={disabled}
        placeholder={
          disabled
            ? "Selecione uma loja primeiro"
            : placeholder
        }
        autoComplete="off"
        style={{
          ...styles.input,
          ...(disabled ? styles.inputDisabled : {}),
        }}
      />

      {aberto && !disabled && (
        <div style={styles.dropdown}>
          {itensFiltrados.length === 0 ? (
            <div style={styles.empty}>
              Nenhum item encontrado.
            </div>
          ) : (
            itensFiltrados.map((item) => {
              const selecionado =
                String(item.id) === String(value);

              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => selecionarItem(item)}
                  style={{
                    ...styles.option,
                    ...(selecionado
                      ? styles.optionSelected
                      : {}),
                  }}
                >
                  <span style={styles.nome}>
                    {item.nomeItem}
                  </span>

                  {item.cmv != null && (
                    <span style={styles.cmv}>
                      CMV:{" "}
                      {Number(item.cmv).toLocaleString(
                        "pt-BR",
                        {
                          style: "currency",
                          currency: "BRL",
                        }
                      )}
                    </span>
                  )}
                </button>
              );
            })
          )}
        </div>
      )}
    </div>
  );
}

const styles = {
  container: {
    position: "relative",
    width: "100%",
  },

  input: {
    width: "100%",
    boxSizing: "border-box",
    padding: "12px 14px",
    borderRadius: "10px",
    border: "1px solid #30363D",
    backgroundColor: "#21262D",
    color: "#F6F8FA",
    outline: "none",
    fontSize: "15px",
  },

  inputDisabled: {
    opacity: 0.55,
    cursor: "not-allowed",
  },

  dropdown: {
    position: "absolute",
    top: "calc(100% + 6px)",
    left: 0,
    right: 0,
    zIndex: 1000,
    maxHeight: "280px",
    overflowY: "auto",
    backgroundColor: "#161B22",
    border: "1px solid #30363D",
    borderRadius: "10px",
    boxShadow: "0 14px 35px rgba(0,0,0,0.45)",
  },

  option: {
    width: "100%",
    border: "none",
    borderBottom: "1px solid #21262D",
    backgroundColor: "transparent",
    color: "#F6F8FA",
    padding: "11px 12px",
    cursor: "pointer",
    textAlign: "left",
    display: "flex",
    justifyContent: "space-between",
    alignItems: "center",
    gap: "14px",
  },

  optionSelected: {
    backgroundColor: "#21262D",
  },

  nome: {
    minWidth: 0,
  },

  cmv: {
    color: "#8B949E",
    fontSize: "12px",
    whiteSpace: "nowrap",
  },

  empty: {
    padding: "14px",
    color: "#8B949E",
    textAlign: "center",
  },
};
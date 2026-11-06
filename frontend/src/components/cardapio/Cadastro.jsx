import { useEffect, useState } from "react";
import { cardapioApi } from "../../services/api";
import { cores, s } from "../monitor/ui";
import { lerPreco } from "./Controles";

/** Escolher foto (JPG/PNG até 5 MB) com prévia. */
export function FotoInput({ arquivo, onEscolher, atual, rotulo = "Foto" }) {
  const [previa, setPrevia] = useState(null);
  useEffect(() => {
    if (!arquivo) {
      setPrevia(null);
      return undefined;
    }
    const url = URL.createObjectURL(arquivo);
    setPrevia(url);
    return () => URL.revokeObjectURL(url);
  }, [arquivo]);
  const mostrar = previa || atual;
  return (
    <label style={{ ...s.label, minWidth: 0 }}>
      {rotulo}
      <span style={{ display: "flex", gap: "8px", alignItems: "center" }}>
        {mostrar ? (
          <img src={mostrar} alt="" style={{ width: "44px", height: "44px", borderRadius: "8px", objectFit: "cover" }} />
        ) : (
          <span style={{ width: "44px", height: "44px", borderRadius: "8px", background: cores.campo, display: "inline-block" }} />
        )}
        <input
          type="file"
          accept="image/jpeg,image/png"
          onChange={(e) => onEscolher(e.target.files?.[0] || null)}
          style={{ color: cores.textoSuave, fontSize: "13px", minWidth: 0, maxWidth: "220px" }}
        />
      </span>
    </label>
  );
}

function Moldura({ titulo, api, children, onCancelar, onSalvar, salvando, erro, textoSalvar = "Salvar no iFood" }) {
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        onSalvar();
      }}
      style={{
        border: `1px solid ${cores.destaque}`,
        borderRadius: "12px",
        padding: "14px",
        margin: "10px 0",
        display: "grid",
        gap: "12px",
        background: "#1B1F27",
      }}
    >
      <div style={{ display: "flex", justifyContent: "space-between", gap: "10px", flexWrap: "wrap", alignItems: "baseline" }}>
        <strong>{titulo}</strong>
        <span style={{ fontFamily: "ui-monospace, Menlo, Consolas, monospace", fontSize: "12px", color: cores.textoSuave }}>{api}</span>
      </div>
      {children}
      {erro && <div style={{ ...s.erro, margin: 0 }}>{erro}</div>}
      <div style={{ display: "flex", gap: "8px", justifyContent: "flex-end" }}>
        <button type="button" style={s.botaoSec} onClick={onCancelar} disabled={salvando}>
          Cancelar
        </button>
        <button type="submit" style={s.botao} disabled={salvando}>
          {salvando || textoSalvar}
        </button>
      </div>
    </form>
  );
}

async function subir(merchantId, arquivo, setSalvando, rotulo = "foto") {
  if (!arquivo) return null;
  setSalvando(`Enviando ${rotulo}…`);
  return cardapioApi.enviarFoto(merchantId, arquivo);
}

export function NovaCategoria({ merchantId, onPronto, onCancelar }) {
  const [nome, setNome] = useState("");
  const [salvando, setSalvando] = useState("");
  const [erro, setErro] = useState("");
  async function salvar() {
    setErro("");
    setSalvando("Criando no iFood…");
    try {
      const r = await cardapioApi.criarCategoria(merchantId, nome);
      onPronto(r, `Categoria "${nome}" criada no iFood (POST /categories).`);
    } catch (e) {
      setErro(e.message);
    } finally {
      setSalvando("");
    }
  }
  return (
    <Moldura titulo="Nova categoria" api="POST /catalog/v2.0/…/categories" onCancelar={onCancelar} onSalvar={salvar} salvando={salvando} erro={erro} textoSalvar="Criar categoria">
      <label style={s.label}>
        Nome da categoria
        <input autoFocus style={s.input} value={nome} onChange={(e) => setNome(e.target.value)} placeholder="Ex.: Teste Homologação" />
      </label>
    </Moldura>
  );
}

/** Novo item (com preço) ou edição de nome/descrição/foto de um item existente. */
export function ItemForm({ merchantId, categoriaId, item, onPronto, onCancelar }) {
  const editando = !!item;
  const [nome, setNome] = useState(item?.nome || "");
  const [descricao, setDescricao] = useState(item?.descricao || "");
  const [preco, setPreco] = useState("");
  const [foto, setFoto] = useState(null);
  const [salvando, setSalvando] = useState("");
  const [erro, setErro] = useState("");

  async function salvar() {
    setErro("");
    try {
      if (!editando && (lerPreco(preco) == null || lerPreco(preco) <= 0)) {
        setErro("Informe o preço (ex.: 25,90)");
        return;
      }
      const imagePath = await subir(merchantId, foto, setSalvando);
      setSalvando("Salvando no iFood…");
      if (editando) {
        const r = await cardapioApi.editarItem(merchantId, item.id, { nome, descricao, imagePath });
        onPronto(r, `Item "${nome}" alterado no iFood (PUT /items).`);
      } else {
        const r = await cardapioApi.criarItem(merchantId, { categoriaId, nome, descricao, preco: lerPreco(preco), imagePath });
        onPronto(r, `Item "${nome}" criado no iFood, ativo (PUT /items).`);
      }
    } catch (e) {
      setErro(e.message);
    } finally {
      setSalvando("");
    }
  }

  return (
    <Moldura
      titulo={editando ? `Editar "${item.nome}"` : "Novo item"}
      api={editando ? "POST /image/upload · PUT /catalog/v2.0/…/items" : "POST /image/upload · PUT /catalog/v2.0/…/items"}
      onCancelar={onCancelar}
      onSalvar={salvar}
      salvando={salvando}
      erro={erro}
      textoSalvar={editando ? "Salvar alterações" : "Criar item"}
    >
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "12px" }}>
        <label style={s.label}>
          Nome
          <input autoFocus style={s.input} value={nome} onChange={(e) => setNome(e.target.value)} />
        </label>
        {!editando && (
          <label style={s.label}>
            Preço (R$)
            <input style={s.input} inputMode="decimal" value={preco} onChange={(e) => setPreco(e.target.value)} placeholder="25,90" />
          </label>
        )}
        <FotoInput arquivo={foto} onEscolher={setFoto} atual={item?.imagem} rotulo={editando ? "Trocar foto" : "Foto"} />
      </div>
      <label style={s.label}>
        Descrição
        <input style={s.input} value={descricao} onChange={(e) => setDescricao(e.target.value)} />
      </label>
      {editando && <span style={s.suave}>Para mudar o preço, clique no preço do item (PATCH /items/price).</span>}
    </Moldura>
  );
}

export function GrupoForm({ merchantId, item, onPronto, onCancelar }) {
  const [grupo, setGrupo] = useState("");
  const [minimo, setMinimo] = useState("0");
  const [maximo, setMaximo] = useState("");
  const [linhas, setLinhas] = useState([
    { nome: "", preco: "", foto: null },
    { nome: "", preco: "", foto: null },
  ]);
  const [salvando, setSalvando] = useState("");
  const [erro, setErro] = useState("");

  function mudar(i, campo, valor) {
    setLinhas((l) => l.map((x, j) => (j === i ? { ...x, [campo]: valor } : x)));
  }

  async function salvar() {
    setErro("");
    try {
      const validas = linhas.filter((l) => l.nome.trim());
      if (!validas.length) {
        setErro("Informe pelo menos um complemento");
        return;
      }
      const complementos = [];
      for (let i = 0; i < validas.length; i++) {
        const l = validas[i];
        const preco = l.preco === "" ? 0 : lerPreco(l.preco);
        if (preco == null) {
          setErro(`Preço inválido em "${l.nome}"`);
          return;
        }
        const imagePath = await subir(merchantId, l.foto, setSalvando, `foto ${i + 1} de ${validas.length}`);
        complementos.push({ nome: l.nome.trim(), preco, imagePath });
      }
      setSalvando("Salvando no iFood…");
      const r = await cardapioApi.criarComplementos(merchantId, item.id, {
        grupo,
        minimo: Number(minimo) || 0,
        maximo: maximo ? Number(maximo) : null,
        complementos,
      });
      onPronto(r, `Grupo "${grupo}" com ${complementos.length} complemento(s) criado em "${item.nome}" (PUT /items).`);
    } catch (e) {
      setErro(e.message);
    } finally {
      setSalvando("");
    }
  }

  return (
    <Moldura
      titulo={`Novo grupo de complementos em "${item.nome}"`}
      api="POST /image/upload · PUT /catalog/v2.0/…/items"
      onCancelar={onCancelar}
      onSalvar={salvar}
      salvando={salvando}
      erro={erro}
      textoSalvar="Criar grupo"
    >
      <div style={{ display: "grid", gridTemplateColumns: "2fr 1fr 1fr", gap: "12px" }}>
        <label style={s.label}>
          Nome do grupo
          <input autoFocus style={s.input} value={grupo} onChange={(e) => setGrupo(e.target.value)} placeholder="Ex.: Adicionais" />
        </label>
        <label style={s.label}>
          Mínimo
          <input style={s.input} inputMode="numeric" value={minimo} onChange={(e) => setMinimo(e.target.value)} />
        </label>
        <label style={s.label}>
          Máximo
          <input style={s.input} inputMode="numeric" value={maximo} onChange={(e) => setMaximo(e.target.value)} placeholder="todos" />
        </label>
      </div>
      {linhas.map((l, i) => (
        <div
          key={i}
          style={{ display: "grid", gridTemplateColumns: "minmax(140px, 2fr) minmax(90px, 1fr) minmax(200px, 2fr) auto", gap: "10px", alignItems: "end" }}
        >
          <label style={s.label}>
            Complemento {i + 1}
            <input style={s.input} value={l.nome} onChange={(e) => mudar(i, "nome", e.target.value)} placeholder="Ex.: Granola" />
          </label>
          <label style={s.label}>
            Preço (R$)
            <input style={s.input} inputMode="decimal" value={l.preco} onChange={(e) => mudar(i, "preco", e.target.value)} placeholder="0,00" />
          </label>
          <FotoInput arquivo={l.foto} onEscolher={(f) => mudar(i, "foto", f)} />
          <button
            type="button"
            style={{ ...s.botaoSec, padding: "8px 10px" }}
            onClick={() => setLinhas((x) => x.filter((_, j) => j !== i))}
            disabled={linhas.length === 1}
            aria-label={`Tirar complemento ${i + 1}`}
          >
            ×
          </button>
        </div>
      ))}
      <div>
        <button type="button" style={s.botaoSec} onClick={() => setLinhas((x) => [...x, { nome: "", preco: "", foto: null }])}>
          + complemento
        </button>
      </div>
    </Moldura>
  );
}

export function ComplementoForm({ merchantId, item, opcao, onPronto, onCancelar }) {
  const [nome, setNome] = useState(opcao.nome || "");
  const [foto, setFoto] = useState(null);
  const [salvando, setSalvando] = useState("");
  const [erro, setErro] = useState("");

  async function salvar() {
    setErro("");
    try {
      const imagePath = await subir(merchantId, foto, setSalvando);
      setSalvando("Salvando no iFood…");
      const r = await cardapioApi.editarComplemento(merchantId, item.id, opcao.id, { nome, imagePath });
      onPronto(r, `Complemento "${nome}" alterado no iFood (PUT /items).`);
    } catch (e) {
      setErro(e.message);
    } finally {
      setSalvando("");
    }
  }

  return (
    <Moldura
      titulo={`Editar complemento "${opcao.nome}"`}
      api="POST /image/upload · PUT /catalog/v2.0/…/items"
      onCancelar={onCancelar}
      onSalvar={salvar}
      salvando={salvando}
      erro={erro}
      textoSalvar="Salvar alterações"
    >
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))", gap: "12px" }}>
        <label style={s.label}>
          Nome
          <input autoFocus style={s.input} value={nome} onChange={(e) => setNome(e.target.value)} />
        </label>
        <FotoInput arquivo={foto} onEscolher={setFoto} rotulo="Trocar foto" />
      </div>
      <span style={s.suave}>Preço e pausa do complemento ficam na linha dele (PATCH /options/price e /options/status).</span>
    </Moldura>
  );
}

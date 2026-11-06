import { useCallback, useEffect, useMemo, useState } from "react";
import { cardapioApi } from "../../services/api";
import { cores, dataHora, desdeAgora, pilula, s } from "../monitor/ui";
import { Interruptor, PrecoEditavel, reais } from "./Controles";
import Historico from "./Historico";

const CONTEXTOS = { DEFAULT: "Delivery", INDOOR: "Salão", WHITELABEL: "Cardápio digital", TAKEOUT: "Retirada" };
const RESTRICOES = {
  SHIFT_UNAVAILABLE: "fora do turno de venda",
  UNAVAILABLE_ON_SHIFT: "fora do turno de venda",
  MISSING_OPTIONS: "complemento obrigatório sem opção ativa",
  REQUIRED_OPTION_GROUP_UNAVAILABLE: "complemento obrigatório sem opção ativa",
};

function normalizar(t) {
  return String(t || "")
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase();
}

function textoRestricao(r) {
  return String(r || "")
    .split(",")
    .map((x) => RESTRICOES[x.trim()] || x.trim())
    .join(", ");
}

function itemParado(item, categoria) {
  return (
    categoria.status === "UNAVAILABLE" ||
    item.status === "UNAVAILABLE" ||
    !!item.naoVendavel ||
    item.grupos.some((g) => g.opcoes.some((o) => o.status === "UNAVAILABLE"))
  );
}

function Complementos({ item, ocupado, onStatus, onPreco }) {
  return (
    <div style={{ display: "grid", gap: "12px", padding: "6px 0 4px 60px" }}>
      {item.grupos.map((g) => (
        <div key={g.id}>
          <div style={{ ...s.suave, marginBottom: "4px" }}>
            {g.nome}
            {g.min !== null && g.max !== null && ` · escolha ${g.min === g.max ? g.max : `${g.min} a ${g.max}`}`}
            {g.status === "UNAVAILABLE" && <span style={{ ...pilula(cores.amarelo), marginLeft: "6px" }}>grupo pausado</span>}
          </div>
          {g.opcoes.map((o) => (
            <div
              key={o.id}
              style={{
                display: "flex",
                alignItems: "center",
                gap: "10px",
                justifyContent: "space-between",
                padding: "3px 0",
                borderBottom: `1px dashed ${cores.borda}`,
                flexWrap: "wrap",
              }}
            >
              <span style={{ fontSize: "14px", flex: "1 1 160px" }}>{o.nome}</span>
              <span style={{ display: "flex", gap: "12px", alignItems: "center" }}>
                <PrecoEditavel
                  valor={o.preco}
                  precoOriginal={o.precoOriginal}
                  permitirZero
                  ocupado={ocupado === `COMPLEMENTO:${o.id}`}
                  onSalvar={(v) => onPreco("COMPLEMENTO", o, v)}
                />
                <Interruptor
                  ativo={o.status !== "UNAVAILABLE"}
                  ocupado={ocupado === `COMPLEMENTO:${o.id}`}
                  rotulo={`Status de ${o.nome}`}
                  onMudar={(ativo) => onStatus("COMPLEMENTO", o, ativo)}
                />
              </span>
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}

function LinhaItem({ item, categoria, ocupado, aberto, onAbrir, onStatus, onPreco }) {
  const nComplementos = item.grupos.reduce((t, g) => t + g.opcoes.length, 0);
  const complementosPausados = item.grupos.reduce(
    (t, g) => t + g.opcoes.filter((o) => o.status === "UNAVAILABLE").length,
    0
  );
  const chave = `ITEM:${item.id}`;
  return (
    <div style={{ borderBottom: `1px solid ${cores.borda}`, padding: "10px 0" }}>
      <div style={{ display: "flex", gap: "12px", alignItems: "center", flexWrap: "wrap" }}>
        {item.imagem ? (
          <img
            src={item.imagem}
            alt=""
            loading="lazy"
            style={{ width: "48px", height: "48px", borderRadius: "8px", objectFit: "cover", flexShrink: 0 }}
          />
        ) : (
          <div
            style={{
              width: "48px",
              height: "48px",
              borderRadius: "8px",
              background: cores.campo,
              display: "grid",
              placeItems: "center",
              color: cores.textoSuave,
              fontSize: "11px",
              flexShrink: 0,
            }}
            title="Sem foto"
          >
            sem foto
          </div>
        )}
        <div style={{ flex: "1 1 220px", minWidth: 0 }}>
          <div style={{ fontWeight: "bold", display: "flex", gap: "6px", flexWrap: "wrap", alignItems: "center" }}>
            {item.nome}
            {item.naoVendavel && (
              <span style={pilula(cores.vermelho)} title={item.naoVendavel}>
                não vendável: {textoRestricao(item.naoVendavel)}
              </span>
            )}
          </div>
          {item.descricao && (
            <div
              style={{ ...s.suave, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}
              title={item.descricao}
            >
              {item.descricao}
            </div>
          )}
          <div style={{ display: "flex", gap: "10px", flexWrap: "wrap", marginTop: "2px" }}>
            {item.codigoExterno && <span style={{ ...s.suave, fontSize: "12px" }}>cód. {item.codigoExterno}</span>}
            {nComplementos > 0 && (
              <button
                type="button"
                onClick={onAbrir}
                style={{
                  background: "transparent",
                  border: "none",
                  color: complementosPausados ? cores.amarelo : cores.azul,
                  cursor: "pointer",
                  padding: 0,
                  fontSize: "12px",
                }}
              >
                {aberto ? "▾" : "▸"} {nComplementos} complemento(s)
                {complementosPausados ? ` · ${complementosPausados} pausado(s)` : ""}
              </button>
            )}
          </div>
        </div>
        <div style={{ display: "flex", gap: "14px", alignItems: "center", marginLeft: "auto" }}>
          <PrecoEditavel
            valor={item.preco}
            precoOriginal={item.precoOriginal}
            ocupado={ocupado === chave}
            onSalvar={(v) => onPreco("ITEM", item, v)}
          />
          <Interruptor
            ativo={item.status !== "UNAVAILABLE"}
            ocupado={ocupado === chave}
            rotulo={`Status de ${item.nome}`}
            onMudar={(ativo) => onStatus("ITEM", item, ativo)}
          />
        </div>
      </div>
      {aberto && <Complementos item={item} ocupado={ocupado} onStatus={onStatus} onPreco={onPreco} />}
      {categoria.status === "UNAVAILABLE" && item.status !== "UNAVAILABLE" && (
        <div style={{ ...s.suave, fontSize: "12px", paddingLeft: "60px" }}>
          Item ativo, mas a categoria inteira está pausada no iFood.
        </div>
      )}
    </div>
  );
}

export default function CardapioLoja({ loja, onMudou }) {
  const [dados, setDados] = useState(null);
  const [erro, setErro] = useState("");
  const [atualizando, setAtualizando] = useState(false);
  const [catalogo, setCatalogo] = useState(0);
  const [busca, setBusca] = useState("");
  const [soParados, setSoParados] = useState(false);
  const [abertos, setAbertos] = useState({});
  const [ocupado, setOcupado] = useState(null);
  const [aviso, setAviso] = useState(null);
  const [verHistorico, setVerHistorico] = useState(false);
  const [versaoHistorico, setVersaoHistorico] = useState(0);

  const carregar = useCallback(
    async (atualizar = false) => {
      if (atualizar) setAtualizando(true);
      try {
        const r = await cardapioApi.cardapio(loja.merchant_id, atualizar);
        setDados(r);
        setErro("");
        if (atualizar && onMudou) onMudou();
      } catch (e) {
        setErro(e.message);
      } finally {
        setAtualizando(false);
      }
    },
    [loja.merchant_id, onMudou]
  );

  useEffect(() => {
    carregar(false);
  }, [carregar]);

  async function executar(chave, acao, sucesso) {
    setOcupado(chave);
    setAviso(null);
    try {
      const alteracao = await acao();
      if (alteracao.situacao === "FALHOU") {
        setAviso({ tipo: "erro", texto: `O iFood recusou: ${alteracao.erro}` });
        return false;
      }
      setAviso({ tipo: "ok", texto: sucesso, alteracao });
      await carregar(false);
      setVersaoHistorico((v) => v + 1);
      if (onMudou) onMudou();
      return true;
    } catch (e) {
      setAviso({ tipo: "erro", texto: e.message });
      return false;
    } finally {
      setOcupado(null);
    }
  }

  const onStatus = (tipo, ent, ativo) =>
    executar(
      `${tipo}:${ent.id}`,
      () => cardapioApi.alterarStatus(loja.merchant_id, tipo, ent.id, ativo ? "AVAILABLE" : "UNAVAILABLE"),
      `${ent.nome} ${ativo ? "ativado" : "pausado"} no iFood.`
    );

  const onPreco = (tipo, ent, valor) =>
    executar(
      `${tipo}:${ent.id}`,
      () => cardapioApi.alterarPreco(loja.merchant_id, tipo, ent.id, valor),
      `${ent.nome}: ${reais(ent.preco)} → ${reais(valor)} no iFood.`
    );

  async function desfazerUltima() {
    if (!aviso?.alteracao) return;
    const a = aviso.alteracao;
    await executar(`${a.tipo}:${a.entidadeId}`, () => cardapioApi.desfazer(a.id), `Desfeito: ${a.nome} voltou ao valor anterior.`);
  }

  const cardapio = dados?.cardapio;
  const catalogos = cardapio?.catalogos || [];
  const cat = catalogos[Math.min(catalogo, Math.max(0, catalogos.length - 1))];

  const categorias = useMemo(() => {
    if (!cat) return [];
    const termo = normalizar(busca.trim());
    return cat.categorias
      .map((k) => ({
        ...k,
        itensVisiveis: k.itens.filter((i) => {
          if (soParados && !itemParado(i, k)) return false;
          if (!termo) return true;
          return (
            normalizar(i.nome).includes(termo) ||
            normalizar(i.codigoExterno).includes(termo) ||
            normalizar(k.nome).includes(termo) ||
            i.grupos.some((g) => g.opcoes.some((o) => normalizar(o.nome).includes(termo)))
          );
        }),
      }))
      .filter((k) => k.itensVisiveis.length > 0);
  }, [cat, busca, soParados]);

  return (
    <div>
      <div
        style={{
          ...s.card,
          padding: "14px 18px",
          marginBottom: "14px",
          display: "flex",
          gap: "12px",
          alignItems: "center",
          flexWrap: "wrap",
        }}
      >
        <div style={{ flex: "1 1 240px" }}>
          <div style={{ fontWeight: "bold", fontSize: "17px" }}>{loja.loja}</div>
          <div style={s.suave}>
            {cardapio
              ? `${dados.itens} itens · ${dados.pausados} parado(s) · lido do iFood ${dataHora(cardapio.lidoEm)} (há ${desdeAgora(cardapio.lidoEm)})`
              : "Lendo o cardápio…"}
          </div>
        </div>
        <button style={s.botaoSec} onClick={() => setVerHistorico((v) => !v)}>
          {verHistorico ? "Fechar histórico" : "Histórico de alterações"}
        </button>
        <button style={s.botaoSec} onClick={() => carregar(true)} disabled={atualizando}>
          {atualizando ? "Lendo do iFood…" : "Atualizar do iFood"}
        </button>
      </div>

      {erro && <div style={s.erro}>{erro}</div>}
      {dados?.erro && (
        <div style={s.aviso}>
          A última leitura automática falhou ({dataHora(dados.erroEm)}): {dados.erro}
        </div>
      )}
      {aviso && (
        <div
          style={{
            ...(aviso.tipo === "ok" ? s.ok : s.erro),
            display: "flex",
            gap: "10px",
            alignItems: "center",
            flexWrap: "wrap",
            position: "sticky",
            top: "8px",
            zIndex: 5,
          }}
        >
          <span style={{ flex: 1 }}>{aviso.texto}</span>
          {aviso.alteracao && (
            <button style={{ ...s.botaoSec, padding: "4px 10px" }} onClick={desfazerUltima}>
              Desfazer
            </button>
          )}
          <button
            style={{ background: "transparent", border: "none", color: "inherit", cursor: "pointer" }}
            onClick={() => setAviso(null)}
            aria-label="Fechar aviso"
          >
            ✕
          </button>
        </div>
      )}

      {verHistorico && (
        <Historico
          merchantId={loja.merchant_id}
          versao={versaoHistorico}
          onDesfeito={() => {
            carregar(false);
            if (onMudou) onMudou();
          }}
        />
      )}

      {cardapio && (
        <>
          {catalogos.length > 1 && (
            <div style={s.abas}>
              {catalogos.map((c, i) => (
                <button
                  key={c.id}
                  style={{ ...s.aba, ...(i === catalogo ? s.abaAtiva : {}) }}
                  onClick={() => setCatalogo(i)}
                >
                  {CONTEXTOS[c.contexto] || c.contexto}
                </button>
              ))}
            </div>
          )}

          <div style={{ display: "flex", gap: "12px", flexWrap: "wrap", alignItems: "center", marginBottom: "14px" }}>
            <input
              style={{ ...s.input, maxWidth: "340px" }}
              placeholder="Buscar item, complemento, código ou categoria…"
              value={busca}
              onChange={(e) => setBusca(e.target.value)}
            />
            <label style={s.check}>
              <input type="checkbox" checked={soParados} onChange={(e) => setSoParados(e.target.checked)} />
              Só itens parados
            </label>
            <span style={{ ...s.suave, marginLeft: "auto" }}>
              As mudanças valem na hora no iFood e não geram alerta no grupo.
            </span>
          </div>

          {catalogos.length === 0 && <div style={{ ...s.card, ...s.vazio }}>Esta loja não tem cardápio no iFood.</div>}
          {cat && categorias.length === 0 && (
            <div style={{ ...s.card, ...s.vazio }}>
              {soParados ? "Nenhum item parado. ✅" : "Nada encontrado com essa busca."}
            </div>
          )}

          <div style={{ display: "grid", gap: "14px" }}>
            {categorias.map((k) => (
              <div key={k.id} style={{ ...s.card, padding: "14px 18px" }}>
                <div style={{ display: "flex", gap: "8px", alignItems: "center", flexWrap: "wrap", marginBottom: "4px" }}>
                  <h3 style={{ margin: 0, fontSize: "16px" }}>{k.nome}</h3>
                  <span style={s.suave}>
                    {k.itensVisiveis.length === k.itens.length
                      ? `${k.itens.length} item(ns)`
                      : `${k.itensVisiveis.length} de ${k.itens.length}`}
                  </span>
                  {k.status === "UNAVAILABLE" && <span style={pilula(cores.amarelo)}>categoria pausada</span>}
                </div>
                {k.itensVisiveis.map((i) => (
                  <LinhaItem
                    key={i.id}
                    item={i}
                    categoria={k}
                    ocupado={ocupado}
                    aberto={!!abertos[i.id] || (!!busca.trim() && i.grupos.some((g) => g.opcoes.some((o) => normalizar(o.nome).includes(normalizar(busca.trim())))))}
                    onAbrir={() => setAbertos((a) => ({ ...a, [i.id]: !a[i.id] }))}
                    onStatus={onStatus}
                    onPreco={onPreco}
                  />
                ))}
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

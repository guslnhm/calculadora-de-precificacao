import { useEffect, useMemo, useState } from "react";
import { baixarPlanilhaCustos, reajusteApi } from "../../services/api";
import { cores, s } from "../monitor/ui";

/**
 * Entrada de um cliente novo: traz os itens do cardápio do iFood para a calculadora (já ligados)
 * e gera a planilha para o cliente preencher o custo.
 */
export default function ItensDoIfood({ lojaId, nomeLoja, onImportado }) {
  const [todas, setTodas] = useState(null);
  const [merchantId, setMerchantId] = useState("");
  const [ocupado, setOcupado] = useState("");
  const [msg, setMsg] = useState(null);

  const [confirmar, setConfirmar] = useState(null);
  const [versao, setVersao] = useState(0);

  useEffect(() => {
    reajusteApi
      .todasLojasIfood()
      .then((l) => {
        setTodas(l);
        const ligada = l.find((x) => x.lojaId === lojaId);
        setMerchantId(ligada ? ligada.merchantId : "");
      })
      .catch((e) => setMsg({ tipo: "erro", texto: e.message }));
  }, [lojaId, versao]);

  const ligadas = (todas || []).filter((l) => l.lojaId === lojaId);

  async function desligar(l) {
    setOcupado("desligar");
    setMsg(null);
    try {
      await reajusteApi.desligarLojaIfood(lojaId, l.merchantId);
      setMsg({ tipo: "ok", texto: `${l.nome} foi desligada desta loja. Os itens continuam na calculadora.` });
      setConfirmar(null);
      setVersao((v) => v + 1);
    } catch (e) {
      setMsg({ tipo: "erro", texto: e.message });
    } finally {
      setOcupado("");
    }
  }

  const ordenadas = useMemo(() => {
    const l = [...(todas || [])];
    l.sort((a, b) => (b.lojaId === lojaId) - (a.lojaId === lojaId) || String(a.nome).localeCompare(String(b.nome)));
    return l;
  }, [todas, lojaId]);

  async function importar() {
    setOcupado("importar");
    setMsg(null);
    try {
      const r = await reajusteApi.importarIfood(lojaId, merchantId || null);
      setMsg({
        tipo: "ok",
        texto:
          `Importado de ${r.lojas.join(", ")}: ${r.criados} item(ns) novo(s), ` +
          `${r.ligadosAExistentes} ligado(s) a itens que já existiam` +
          (r.jaLigados ? `, ${r.jaLigados} já estavam ligados` : "") +
          ". Agora baixe a planilha de custos.",
      });
      setVersao((v) => v + 1);
      if (onImportado) onImportado();
    } catch (e) {
      setMsg({ tipo: "erro", texto: e.message });
    } finally {
      setOcupado("");
    }
  }

  async function baixar() {
    setOcupado("planilha");
    setMsg(null);
    try {
      const nome = `custos-${String(nomeLoja || lojaId)
        .normalize("NFD")
        .replace(/[̀-ͯ]/g, "")
        .replace(/[^a-zA-Z0-9]+/g, "-")
        .toLowerCase()}.xlsx`;
      await baixarPlanilhaCustos(lojaId, nome);
    } catch (e) {
      setMsg({ tipo: "erro", texto: e.message });
    } finally {
      setOcupado("");
    }
  }

  const ligada = (todas || []).find((x) => x.merchantId === merchantId);
  const deOutra = ligada && ligada.lojaId && ligada.lojaId !== lojaId;

  return (
    <div style={{ display: "grid", gap: "12px" }}>
      <div style={{ display: "grid", gap: "6px", lineHeight: 1.5, fontSize: "14px" }}>
        <span>
          <strong>1.</strong> Importe os itens do cardápio do iFood (sem complementos). Eles entram com o preço atual
          do iFood e sem custo.
        </span>
        <span>
          <strong>2.</strong> Baixe a planilha, mande para o cliente preencher a coluna de custo e suba de volta em
          “Importar planilha”: o custo cai em cada item, sem duplicar.
        </span>
      </div>
      {ligadas.length > 0 && (
        <div style={{ display: "grid", gap: "6px" }}>
          <span style={s.label}>Lojas do iFood ligadas a esta loja</span>
          {ligadas.map((l) => (
            <div
              key={l.merchantId}
              style={{
                display: "flex",
                alignItems: "center",
                gap: "8px",
                flexWrap: "wrap",
                padding: "8px 12px",
                border: `1px solid ${cores.bordaCampo}`,
                borderRadius: "10px",
              }}
            >
              <span style={{ flex: 1, fontSize: "14px" }}>{l.nome}</span>
              {confirmar === l.merchantId ? (
                <>
                  <span style={{ color: cores.amarelo, fontSize: "13px" }}>Desligar?</span>
                  <button type="button" style={{ ...s.botaoPerigo, padding: "5px 10px" }} disabled={!!ocupado} onClick={() => desligar(l)}>
                    Sim
                  </button>
                  <button type="button" style={{ ...s.botaoSec, padding: "5px 10px" }} onClick={() => setConfirmar(null)}>
                    Não
                  </button>
                </>
              ) : (
                <button type="button" style={{ ...s.botaoSec, padding: "5px 10px" }} onClick={() => setConfirmar(l.merchantId)}>
                  Desligar
                </button>
              )}
            </div>
          ))}
        </div>
      )}
      <label style={s.label}>
        Loja no iFood
        <select style={s.input} value={merchantId} onChange={(e) => setMerchantId(e.target.value)}>
          <option value="">
            {todas === null
              ? "Carregando…"
              : ligadas.length
                ? "Todas as lojas do iFood já ligadas a esta loja"
                : "Escolha a loja do iFood"}
          </option>
          {ordenadas.map((l) => (
            <option key={l.merchantId} value={l.merchantId}>
              {l.nome}
              {l.lojaId === lojaId ? " (ligada)" : l.lojaId ? " (ligada a outra loja)" : ""}
            </option>
          ))}
        </select>
      </label>
      {deOutra && (
        <div style={{ ...s.aviso, margin: 0 }}>
          Esta loja do iFood está ligada a outra loja da calculadora. Ao importar, ela sai de lá e passa para esta
          (os itens da outra loja continuam onde estão).
        </div>
      )}
      {msg && <div style={{ ...(msg.tipo === "ok" ? s.ok : s.erro), margin: 0 }}>{msg.texto}</div>}
      <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
        <button type="button" style={s.botao} onClick={importar} disabled={!!ocupado}>
          {ocupado === "importar" ? "Importando…" : "Importar itens do iFood"}
        </button>
        <button type="button" style={s.botaoSec} onClick={baixar} disabled={!!ocupado}>
          {ocupado === "planilha" ? "Gerando…" : "Baixar planilha de custos"}
        </button>
      </div>
    </div>
  );
}

import { useCallback, useEffect, useState } from "react";
import { whatsappApi } from "../../services/api";
import { cores, pilula, s } from "./ui";

const NOMES = { evolution: "Evolution API", zapi: "Z-API", console: "nenhum (só log)" };

/**
 * Mostra se o número do WhatsApp está conectado e, na Evolution, conecta pelo QR Code aqui mesmo.
 * instancia: número extra (vazio = o principal); nome: como ele aparece na tela.
 */
export default function ConexaoWhatsapp({ instancia = null, nome: nomeNumero = null, compacto = false }) {
  const [situacao, setSituacao] = useState(null);
  const [qr, setQr] = useState(null);
  const [carregandoQr, setCarregandoQr] = useState(false);
  const [erroQr, setErroQr] = useState("");
  // conexão por código (o celular do número não está aqui para ler o QR)
  const [modoCodigo, setModoCodigo] = useState(false);
  const [telefone, setTelefone] = useState("");
  const [codigo, setCodigo] = useState(null);
  const [numeroUsado, setNumeroUsado] = useState("");
  const [copiado, setCopiado] = useState(false);

  const ler = useCallback(async () => {
    let atual;
    try {
      atual = await whatsappApi.situacao(instancia);
    } catch (e) {
      atual = { erro: e.message };
    }
    setSituacao(atual);
    return atual;
  }, [instancia]);

  useEffect(() => {
    ler();
  }, [ler]);

  const gerarQr = useCallback(async () => {
    setCarregandoQr(true);
    setErroQr("");
    try {
      const r = await whatsappApi.conectar(instancia);
      if (r.conectado) {
        setQr(null);
        await ler();
      } else {
        setQr(r);
      }
    } catch (e) {
      setErroQr(e.message);
    } finally {
      setCarregandoQr(false);
    }
  }, [ler, instancia]);

  const gerarCodigo = useCallback(async () => {
    setCarregandoQr(true);
    setErroQr("");
    try {
      const r = await whatsappApi.conectarPorCodigo(instancia, telefone);
      if (r.conectado) {
        setCodigo(null);
        setModoCodigo(false);
        await ler();
      } else if (r.codigoPareamento) {
        setCodigo(r.codigoPareamento);
        setNumeroUsado(r.numeroUsado || "");
      } else {
        setErroQr(r.mensagem || "Não veio o código. Tente de novo.");
      }
    } catch (e) {
      setErroQr(e.message);
    } finally {
      setCarregandoQr(false);
    }
  }, [instancia, telefone, ler]);

  // Com o código na tela: confere a conexão a cada 4s (não renova sozinho: um código novo invalida o anterior).
  useEffect(() => {
    if (!codigo) return undefined;
    const conferir = setInterval(async () => {
      const atual = await ler();
      if (atual.connected) {
        setCodigo(null);
        setModoCodigo(false);
      }
    }, 4000);
    return () => clearInterval(conferir);
  }, [codigo, ler]);

  // Enquanto o QR está na tela: confere a conexão a cada 4s e troca o QR a cada 30s (ele expira).
  useEffect(() => {
    if (!qr) return undefined;
    const conferir = setInterval(async () => {
      const atual = await ler();
      if (atual.connected) setQr(null);
    }, 4000);
    const renovar = setTimeout(gerarQr, 30000);
    return () => {
      clearInterval(conferir);
      clearTimeout(renovar);
    };
  }, [qr, ler, gerarQr]);

  if (!situacao) return null;

  const provider = situacao.provider;
  const nome = NOMES[provider] || "WhatsApp";
  const conectado = !situacao.erro && (situacao.connected === true || situacao.conectado === true);

  if (situacao.erro) {
    return <div style={s.erro}>Não consegui falar com a {nome}: {situacao.erro}</div>;
  }

  if (provider === "console") {
    return (
      <div style={s.aviso}>
        Nenhum WhatsApp configurado no servidor: as mensagens só vão para o log. Confira WHATSAPP_PROVIDER e as
        variáveis do provedor no Railway.
      </div>
    );
  }

  if (conectado) {
    return (
      <div style={{ marginBottom: compacto ? 0 : "14px" }}>
        <span style={pilula(cores.verde)}>● {nomeNumero ? "Conectado" : `WhatsApp conectado (${nome})`}</span>
      </div>
    );
  }

  if (provider !== "evolution") {
    return (
      <div style={s.aviso}>
        O número parece desconectado. Abra o painel da {nome} e leia o QR Code de novo.
      </div>
    );
  }

  return (
    <div style={compacto ? { marginTop: "6px" } : { ...s.card, marginBottom: "16px", borderColor: cores.amarelo }}>
      {!compacto && <h3 style={s.cardTitulo}>Conectar o número do WhatsApp</h3>}
      {modoCodigo && !qr && (
        <div style={{ display: "grid", gap: "10px", maxWidth: "560px" }}>
          {!codigo ? (
            <>
              <p style={{ ...s.suave, margin: 0 }}>
                Para conectar sem ler o QR Code: informe o número do WhatsApp que vai enviar. O site gera um código de 8
                letras para a pessoa digitar no próprio celular.
              </p>
              <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
                <input
                  style={{ ...s.input, flex: "1 1 200px" }}
                  inputMode="tel"
                  placeholder="Número com DDD, ex.: 61 99999-8888"
                  value={telefone}
                  onChange={(e) => setTelefone(e.target.value)}
                />
                <button style={s.botao} onClick={gerarCodigo} disabled={carregandoQr || telefone.replace(/\D/g, "").length < 10}>
                  {carregandoQr ? "Gerando…" : "Gerar código"}
                </button>
                <button style={s.botaoSec} onClick={() => setModoCodigo(false)}>
                  Voltar ao QR Code
                </button>
              </div>
            </>
          ) : (
            <>
              <div style={{ fontSize: "13px", color: cores.textoSuave }}>
                Código de conexão{numeroUsado ? ` para o número ${numeroUsado.replace(/^55(\d{2})(\d+)(\d{4})$/, "+55 $1 $2-$3")}` : ""}
              </div>
              <div style={{ fontSize: "34px", fontWeight: 700, letterSpacing: "0.12em", color: cores.texto, fontVariantNumeric: "tabular-nums" }}>
                {codigo.length === 8 ? `${codigo.slice(0, 4)}-${codigo.slice(4)}` : codigo}
              </div>
              <ol style={{ margin: 0, paddingLeft: "18px", lineHeight: 1.8, fontSize: "14px" }}>
                <li>No celular do número, abra o WhatsApp (ou WhatsApp Business).</li>
                <li>
                  Toque em <strong>⋮ (ou Configurações) → Dispositivos conectados → Conectar dispositivo</strong>.
                </li>
                <li>
                  Na tela da câmera, toque em <strong>Conectar com número de telefone</strong> e digite o código acima.
                </li>
                <li>O código vale por pouco tempo. Se não der certo, gere outro (o anterior deixa de valer).</li>
              </ol>
              <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
                <button
                  style={s.botaoSec}
                  onClick={async () => {
                    const texto = `Código para conectar o WhatsApp ao monitor: ${codigo}\n\nNo WhatsApp: ⋮ (ou Configurações) → Dispositivos conectados → Conectar dispositivo → "Conectar com número de telefone" e digite o código.`;
                    try {
                      await navigator.clipboard.writeText(texto);
                      setCopiado(true);
                      setTimeout(() => setCopiado(false), 2000);
                    } catch {
                      window.prompt("Copie o texto:", texto);
                    }
                  }}
                >
                  {copiado ? "Copiado ✓" : "Copiar código e instruções"}
                </button>
                <button style={s.botaoSec} onClick={gerarCodigo} disabled={carregandoQr}>
                  {carregandoQr ? "Gerando…" : "Gerar outro código"}
                </button>
                <button style={s.botaoSec} onClick={() => { setCodigo(null); setModoCodigo(false); }}>
                  Cancelar
                </button>
              </div>
              <div style={s.suave}>Esta tela muda sozinha quando o celular conectar.</div>
            </>
          )}
        </div>
      )}
      {!qr && !modoCodigo && (
        <>
          <p style={{ ...s.suave, marginTop: 0 }}>
            {situacao.state === "sem_instancia"
              ? "Primeira vez: clique abaixo para gerar o QR Code."
              : "O número está desconectado. Gere um QR Code novo para conectar."}
          </p>
          <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
            <button style={s.botao} onClick={gerarQr} disabled={carregandoQr}>
              {carregandoQr ? "Gerando QR Code…" : "Conectar número"}
            </button>
            <button style={s.botaoSec} onClick={() => setModoCodigo(true)}>
              Celular não está aqui? Conectar por código
            </button>
          </div>
        </>
      )}
      {qr && (
        <div style={{ display: "flex", gap: "20px", flexWrap: "wrap", alignItems: "flex-start" }}>
          {qr.qrCode ? (
            <img
              src={qr.qrCode}
              alt="QR Code para conectar o WhatsApp"
              style={{ width: "240px", height: "240px", background: "#fff", borderRadius: "10px", padding: "8px" }}
            />
          ) : (
            <div style={{ ...s.suave, width: "240px" }}>{qr.mensagem || "Gerando o QR Code…"}</div>
          )}
          <ol style={{ margin: 0, paddingLeft: "18px", lineHeight: 1.8, fontSize: "14px", flex: "1 1 220px" }}>
            <li>No celular {nomeNumero ? `do número "${nomeNumero}"` : "do número Business"}, abra o WhatsApp (ou WhatsApp Business).</li>
            <li>
              Toque em <strong>⋮ → Dispositivos conectados → Conectar dispositivo</strong>.
            </li>
            <li>Aponte a câmera para o QR Code ao lado.</li>
            <li>Esta tela muda sozinha quando conectar. O QR é renovado a cada 30 segundos.</li>
            {qr.codigoPareamento && (
              <li>
                Ou use o código de pareamento: <strong>{qr.codigoPareamento}</strong>
              </li>
            )}
          </ol>
        </div>
      )}
      {erroQr && <div style={{ ...s.erro, margin: "12px 0 0" }}>{erroQr}</div>}
    </div>
  );
}

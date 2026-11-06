import { useCallback, useEffect, useState } from "react";
import { whatsappApi } from "../../services/api";
import { cores, pilula, s } from "./ui";

const NOMES = { evolution: "Evolution API", zapi: "Z-API", console: "nenhum (só log)" };

/**
 * Mostra se o número do WhatsApp está conectado e, na Evolution, conecta pelo QR Code aqui mesmo.
 */
export default function ConexaoWhatsapp() {
  const [situacao, setSituacao] = useState(null);
  const [qr, setQr] = useState(null);
  const [carregandoQr, setCarregandoQr] = useState(false);
  const [erroQr, setErroQr] = useState("");

  const ler = useCallback(async () => {
    let atual;
    try {
      atual = await whatsappApi.situacao();
    } catch (e) {
      atual = { erro: e.message };
    }
    setSituacao(atual);
    return atual;
  }, []);

  useEffect(() => {
    ler();
  }, [ler]);

  const gerarQr = useCallback(async () => {
    setCarregandoQr(true);
    setErroQr("");
    try {
      const r = await whatsappApi.conectar();
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
  }, [ler]);

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
      <div style={{ marginBottom: "14px" }}>
        <span style={pilula(cores.verde)}>● WhatsApp conectado ({nome})</span>
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
    <div style={{ ...s.card, marginBottom: "16px", borderColor: cores.amarelo }}>
      <h3 style={s.cardTitulo}>Conectar o número do WhatsApp</h3>
      {!qr && (
        <>
          <p style={{ ...s.suave, marginTop: 0 }}>
            {situacao.state === "sem_instancia"
              ? "Primeira vez: clique abaixo para gerar o QR Code."
              : "O número está desconectado. Gere um QR Code novo para conectar."}
          </p>
          <button style={s.botao} onClick={gerarQr} disabled={carregandoQr}>
            {carregandoQr ? "Gerando QR Code…" : "Conectar número"}
          </button>
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
            <li>No celular do número Business, abra o WhatsApp Business.</li>
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

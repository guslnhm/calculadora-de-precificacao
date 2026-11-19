import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { conviteApi, salvarSessao } from "../services/api";
import TelaPublica, { Aviso, Campo } from "../components/acesso/TelaPublica";
import { botaoGrande } from "../components/acesso/apoio";
import { cor } from "../components/painel/fmt";

// Link de nova senha (gerado pela equipe na tela de Acessos).

export default function NovaSenhaPage({ onEntrar }) {
  const { token } = useParams();
  const navigate = useNavigate();
  const [link, setLink] = useState(null);
  const [erroCarga, setErroCarga] = useState("");
  const [senha, setSenha] = useState("");
  const [confirmar, setConfirmar] = useState("");
  const [erro, setErro] = useState("");
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    conviteApi.verLinkSenha(token).then(setLink).catch((e) => setErroCarga(e.message));
  }, [token]);

  async function enviar(e) {
    e.preventDefault();
    setErro("");
    if (senha.length < 8) return setErro("A senha precisa ter pelo menos 8 caracteres");
    if (senha !== confirmar) return setErro("As senhas não são iguais");
    setEnviando(true);
    try {
      const r = await conviteApi.novaSenha(token, senha);
      salvarSessao(r);
      onEntrar?.(r);
      navigate("/painel", { replace: true });
    } catch (e2) {
      setErro(e2.message);
    } finally {
      setEnviando(false);
    }
  }

  if (erroCarga) return <TelaPublica titulo="Não deu para abrir o link"><Aviso>{erroCarga}</Aviso></TelaPublica>;
  if (!link) return <TelaPublica titulo="Abrindo..." />;
  if (!link.valido) {
    return (
      <TelaPublica titulo="Link indisponível">
        <Aviso>{link.motivo}</Aviso>
        <Link to="/" style={{ color: cor.azul, fontSize: 14 }}>Ir para a tela de login</Link>
      </TelaPublica>
    );
  }
  return (
    <TelaPublica titulo="Crie uma senha nova" subtitulo={`${link.nome || ""} · ${link.email || ""}`}>
      <form onSubmit={enviar} style={{ display: "grid", gap: 14 }}>
        <input type="email" value={link.email || ""} autoComplete="username" readOnly hidden />
        <Campo rotulo="Senha nova" type="password" value={senha} onChange={(e) => setSenha(e.target.value)}
               autoComplete="new-password" dica="Pelo menos 8 caracteres" required />
        <Campo rotulo="Repita a senha" type="password" value={confirmar} onChange={(e) => setConfirmar(e.target.value)}
               autoComplete="new-password" required />
        {erro && <Aviso>{erro}</Aviso>}
        <button style={botaoGrande} disabled={enviando}>{enviando ? "Salvando..." : "Salvar e entrar"}</button>
      </form>
    </TelaPublica>
  );
}

import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { conviteApi, salvarSessao } from "../services/api";
import TelaPublica, { Aviso, Campo } from "../components/acesso/TelaPublica";
import { botaoGrande } from "../components/acesso/apoio";
import { cor } from "../components/painel/fmt";

// Cadastro pelo link de convite: nome, e-mail, data de nascimento e senha. Entra direto no painel.

export default function ConvitePage({ onEntrar }) {
  const { token } = useParams();
  const navigate = useNavigate();
  const [convite, setConvite] = useState(null);
  const [erroCarga, setErroCarga] = useState("");
  const [form, setForm] = useState({ nome: "", email: "", dataNascimento: "", senha: "", confirmar: "" });
  const [erro, setErro] = useState("");
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    conviteApi.ver(token).then(setConvite).catch((e) => setErroCarga(e.message));
  }, [token]);

  const mudar = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  async function enviar(e) {
    e.preventDefault();
    setErro("");
    if (!form.dataNascimento) return setErro("Informe a sua data de nascimento");
    if (form.senha.length < 8) return setErro("A senha precisa ter pelo menos 8 caracteres");
    if (form.senha !== form.confirmar) return setErro("As senhas não são iguais");
    setEnviando(true);
    try {
      const r = await conviteApi.aceitar(token, {
        nome: form.nome, email: form.email, dataNascimento: form.dataNascimento, senha: form.senha,
      });
      salvarSessao(r);
      onEntrar?.(r);
      navigate("/painel", { replace: true });
    } catch (e2) {
      setErro(e2.message);
    } finally {
      setEnviando(false);
    }
  }

  if (erroCarga) {
    return <TelaPublica titulo="Não deu para abrir o convite"><Aviso>{erroCarga}</Aviso></TelaPublica>;
  }
  if (!convite) {
    return <TelaPublica titulo="Abrindo o convite..." />;
  }
  if (!convite.valido) {
    return (
      <TelaPublica titulo="Link indisponível">
        <Aviso>{convite.motivo}</Aviso>
        <Link to="/" style={{ color: cor.azul, fontSize: 14 }}>Ir para a tela de login</Link>
      </TelaPublica>
    );
  }

  const lojas = convite.lojas || [];
  return (
    <TelaPublica
      titulo="Crie o seu acesso"
      subtitulo={convite.perfil === "MENTORADO"
        ? "Você vai acompanhar os resultados da sua operação no iFood, atualizados todo dia."
        : "Acesso da equipe ao sistema."}>
      {lojas.length > 0 && (
        <div style={{ background: cor.campo, border: `1px solid ${cor.bordaCampo}`, borderRadius: 10, padding: "10px 12px" }}>
          <div style={{ fontSize: 12, color: cor.textoSuave, marginBottom: 4 }}>{lojas.length === 1 ? "Loja" : "Lojas"}</div>
          {lojas.map((l) => <div key={l} style={{ fontWeight: 600, fontSize: 15 }}>{l}</div>)}
        </div>
      )}
      <form onSubmit={enviar} style={{ display: "grid", gap: 14 }}>
        <Campo rotulo="Seu nome" value={form.nome} onChange={mudar("nome")} autoComplete="name" required />
        <Campo rotulo="E-mail" type="email" value={form.email} onChange={mudar("email")} autoComplete="email"
               autoCapitalize="none" required />
        <Campo rotulo="Data de nascimento" type="date" value={form.dataNascimento} onChange={mudar("dataNascimento")}
               autoComplete="bday" required />
        <Campo rotulo="Crie uma senha" type="password" value={form.senha} onChange={mudar("senha")}
               autoComplete="new-password" dica="Pelo menos 8 caracteres" required />
        <Campo rotulo="Repita a senha" type="password" value={form.confirmar} onChange={mudar("confirmar")}
               autoComplete="new-password" required />
        {erro && <Aviso>{erro}</Aviso>}
        <button style={botaoGrande} disabled={enviando}>{enviando ? "Criando..." : "Criar acesso e entrar"}</button>
        <Aviso tipo="info">
          Já tem acesso? Use o mesmo e-mail e a sua senha atual: a loja nova entra junto com as que você já vê.
        </Aviso>
      </form>
    </TelaPublica>
  );
}

import { useState, type FormEvent } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { api } from "../api/client";
import { explicarErro } from "../api/erros";
import { ErroDeCampo, useErrosDeCampo } from "../componentes/ErroDeCampo";
import { useAuth } from "../auth/AuthContext";

type Modo = "entrar" | "cadastrar" | "recuperar" | "redefinir";

const TITULOS: Record<Modo, string> = {
  entrar: "Acesse seu consultório",
  cadastrar: "Crie seu consultório",
  recuperar: "Recuperar acesso",
  redefinir: "Escolha uma senha nova",
};

const SUBTITULOS: Record<Modo, string> = {
  entrar: "Use o e-mail e a senha cadastrados.",
  cadastrar: "Leva um minuto. Você já entra com o acervo de alimentos carregado.",
  recuperar: "Informe o e-mail da conta. Enviamos um link que vale por uma hora.",
  redefinir: "Ao salvar, as sessões abertas nesta conta são encerradas.",
};

/** O que se tentava fazer, para a frase de erro. */
const ACOES_ERRO: Record<Modo, string> = {
  entrar: "entrar na sua conta",
  cadastrar: "criar a sua conta",
  recuperar: "enviar o link de recuperação",
  redefinir: "redefinir a sua senha",
};

const ACOES: Record<Modo, string> = {
  entrar: "Entrar",
  cadastrar: "Criar conta",
  recuperar: "Enviar link",
  redefinir: "Salvar senha nova",
};

export default function Acesso() {
  const { usuario, carregando, entrar, cadastrar } = useAuth();
  const local = useLocation() as { state?: { de?: string } };

  // Chegar com ?token= na URL é o caminho do link recebido por e-mail.
  const tokenDaUrl = new URLSearchParams(window.location.search).get("token");
  const [modo, setModo] = useState<Modo>(tokenDaUrl ? "redefinir" : "entrar");
  const [token] = useState(tokenDaUrl ?? "");
  const [aviso, setAviso] = useState<string | null>(null);
  const [nome, setNome] = useState("");
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [crn, setCrn] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);
  const campos = useErrosDeCampo();

  if (carregando) {
    return <p className="carregando">Carregando…</p>;
  }
  if (usuario) {
    return <Navigate to={local.state?.de ?? "/pacientes"} replace />;
  }

  async function enviar(evento: FormEvent) {
    evento.preventDefault();
    setErro(null);
    setAviso(null);
    setEnviando(true);
    try {
      if (modo === "recuperar") {
        await api.acesso.recuperarSenha(email.trim());
        // A mensagem é a mesma exista o e-mail ou não: dizer "não encontrado"
        // permitiria descobrir quem tem conta.
        setAviso(
          "Se houver uma conta com esse e-mail, o link de redefinição foi enviado. " +
            "Ele vale por uma hora.",
        );
        setEnviando(false);
        return;
      }
      if (modo === "redefinir") {
        await api.acesso.redefinirSenha(token, senha);
        setAviso("Senha redefinida. Entre com a senha nova.");
        setModo("entrar");
        setSenha("");
        setEnviando(false);
        return;
      }
      if (modo === "entrar") {
        await entrar(email.trim(), senha);
      } else {
        await cadastrar({
          nome: nome.trim(),
          email: email.trim(),
          senha,
          crn: crn.trim() || undefined,
        });
      }
    } catch (e) {
      // Erro de validação gruda no campo; o resto vira uma frase que diz o que
      // se tentava fazer.
      if (!campos.aplicar(e)) {
        setErro(explicarErro(e, modo === "entrar" ? "entrar na sua conta" : ACOES_ERRO[modo]));
      }
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="tela-acesso">
      {/* O painel escuro é o mesmo trilho que emoldura o consultório depois do
          login: quem entra já reconhece onde está. */}
      <aside className="acesso-marca">
        <div className="marca">
          NutriPlan
          <small>consultório</small>
        </div>
        <div>
          <h2>Cada plano começa por um dia.</h2>
          <p>
            Monte a rotina do paciente hora a hora, com a medida que ele usa na
            cozinha — colher, concha, fatia — e não só o peso em gramas.
          </p>
        </div>
        <footer>23.945 alimentos · TACO · IBGE · Open Food Facts</footer>
      </aside>

      <div className="acesso-formulario">
      <form className="caixa-acesso" onSubmit={enviar}>
        <div>
          {/* O título nomeia a tela; o botão nomeia a ação. Repetir "Entrar"
              nos dois faria um deles não informar nada. */}
          <h1>{TITULOS[modo]}</h1>
          <p className="discreto" style={{ margin: "0.4rem 0 0" }}>
            {SUBTITULOS[modo]}
          </p>
        </div>

        {erro && (
          <div className="aviso erro" role="alert">
            {erro}
          </div>
        )}

        {aviso && (
          <div className="aviso ok" role="status">
            {aviso}
          </div>
        )}

        {modo === "cadastrar" && (
          <div className="campo">
            <label htmlFor="nome">Nome completo</label>
            <input
              id="nome"
              name="nome"
              value={nome}
              onChange={(e) => setNome(e.target.value)}
              required
              autoComplete="name"
              {...campos.props("nome")}
            />
            <ErroDeCampo campo="nome" erros={campos.erros} />
          </div>
        )}

        {modo !== "redefinir" && (
        <div className="campo">
          <label htmlFor="email">E-mail</label>
          <input
            id="email"
            name="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="email"
            {...campos.props("email")}
          />
          <ErroDeCampo campo="email" erros={campos.erros} />
        </div>
        )}

        {modo !== "recuperar" && (
        <div className="campo">
          <label htmlFor="senha">{modo === "redefinir" ? "Nova senha" : "Senha"}</label>
          <input
            id="senha"
            name="senha"
            type="password"
            value={senha}
            onChange={(e) => setSenha(e.target.value)}
            required
            minLength={modo === "entrar" ? undefined : 8}
            autoComplete={modo === "entrar" ? "current-password" : "new-password"}
            {...campos.props("senha")}
          />
          <ErroDeCampo campo="senha" erros={campos.erros} />
          {modo !== "entrar" && <span className="minusculo">Mínimo de 8 caracteres.</span>}
        </div>
        )}

        {modo === "cadastrar" && (
          <div className="campo">
            <label htmlFor="crn">CRN (opcional)</label>
            <input
              id="crn"
              name="crn"
              value={crn}
              onChange={(e) => setCrn(e.target.value)}
              {...campos.props("crn")}
            />
            <ErroDeCampo campo="crn" erros={campos.erros} />
          </div>
        )}

        <button className="botao" type="submit" disabled={enviando}>
          {enviando ? "Aguarde…" : ACOES[modo]}
        </button>

        {modo === "entrar" && (
          <button
            type="button"
            className="ligacao minusculo"
            style={{ alignSelf: "center" }}
            onClick={() => {
              setModo("recuperar");
              setErro(null);
              setAviso(null);
            }}
          >
            Esqueci minha senha
          </button>
        )}

        <button
          type="button"
          className="botao secundario"
          style={{ justifyContent: "center" }}
          onClick={() => {
            setModo(modo === "cadastrar" ? "entrar" : modo === "entrar" ? "cadastrar" : "entrar");
            setErro(null);
            setAviso(null);
          }}
        >
          {modo === "cadastrar" ? "Já tenho conta" : modo === "entrar" ? "Criar uma conta" : "Voltar"}
        </button>
      </form>
      </div>
    </div>
  );
}

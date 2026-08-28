import { useState, type FormEvent } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { FieldError, useFieldErrors } from "../componentes/FieldError";
import { useAuth } from "../auth/AuthContext";

type Mode = "login" | "register" | "recover" | "reset";

const TITLES: Record<Mode, string> = {
  login: "Acesse seu consultório",
  register: "Crie seu consultório",
  recover: "Recuperar acesso",
  reset: "Escolha uma senha nova",
};

const SUBTITLES: Record<Mode, string> = {
  login: "Use o e-mail e a senha cadastrados.",
  register: "Leva um minuto. Você já entra com o acervo de alimentos carregado.",
  recover: "Informe o e-mail da conta. Enviamos um link que vale por uma hora.",
  reset: "Ao salvar, as sessões abertas nesta conta são encerradas.",
};

/** What was being attempted, for the error sentence. */
const ACTIONS_ERROR: Record<Mode, string> = {
  login: "entrar na sua conta",
  register: "criar a sua conta",
  recover: "enviar o link de recuperação",
  reset: "redefinir a sua senha",
};

const ACTIONS: Record<Mode, string> = {
  login: "Entrar",
  register: "Criar conta",
  recover: "Enviar link",
  reset: "Salvar senha nova",
};

export default function Access() {
  const { user, loading, login, register } = useAuth();
  const local = useLocation() as { state?: { from?: string } };

  // Arriving with ?token= in the URL is the path of the link received by email.
  const urlToken = new URLSearchParams(window.location.search).get("token");
  const [mode, setMode] = useState<Mode>(urlToken ? "reset" : "login");
  const [token] = useState(urlToken ?? "");
  const [warning, setWarning] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [crn, setCrn] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const fields = useFieldErrors();

  if (loading) {
    return <p className="loading">Loading…</p>;
  }
  if (user) {
    return <Navigate to={local.state?.from ?? "/patients"} replace />;
  }

  async function send(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setWarning(null);
    setSending(true);
    try {
      if (mode === "recover") {
        await api.access.recoverPassword(email.trim());
        // The message is the same whether the email exists or not: saying "not
        // found" would let someone discover who has an account.
        setWarning(
          "Se houver uma conta com esse e-mail, o link de redefinição foi enviado. " +
            "Ele vale por uma hora.",
        );
        setSending(false);
        return;
      }
      if (mode === "reset") {
        await api.access.resetPassword(token, password);
        setWarning("Senha redefinida. Entre com a senha nova.");
        setMode("login");
        setPassword("");
        setSending(false);
        return;
      }
      if (mode === "login") {
        await login(email.trim(), password);
      } else {
        await register({
          name: name.trim(),
          email: email.trim(),
          password,
          crn: crn.trim() || undefined,
        });
      }
    } catch (e) {
      // A validation error sticks to the field; the rest becomes a sentence that
      // says what was being attempted.
      if (!fields.apply(e)) {
        setError(explainError(e, mode === "login" ? "entrar na sua conta" : ACTIONS_ERROR[mode]));
      }
    } finally {
      setSending(false);
    }
  }

  return (
    <div className="screen-access">
      {/* The dark panel is the same rail that frames the practice after login:
          whoever comes in already recognizes where they are. */}
      <aside className="access-brand">
        <div className="brand">
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

      <div className="access-form">
      <form className="box-access" onSubmit={send}>
        <div>
          {/* The title names the screen; the button names the action. Repeating
              "Entrar" in both would make one of them say nothing. */}
          <h1>{TITLES[mode]}</h1>
          <p className="discreto" style={{ margin: "0.4rem 0 0" }}>
            {SUBTITLES[mode]}
          </p>
        </div>

        {error && (
          <div className="warning error" role="alert">
            {error}
          </div>
        )}

        {warning && (
          <div className="warning ok" role="status">
            {warning}
          </div>
        )}

        {mode === "register" && (
          <div className="field">
            <label htmlFor="name">Nome completo</label>
            <input
              id="name"
              name="name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              autoComplete="name"
              {...fields.props("name")}
            />
            <FieldError field="name" errors={fields.errors} />
          </div>
        )}

        {mode !== "reset" && (
        <div className="field">
          <label htmlFor="email">E-mail</label>
          <input
            id="email"
            name="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="email"
            {...fields.props("email")}
          />
          <FieldError field="email" errors={fields.errors} />
        </div>
        )}

        {mode !== "recover" && (
        <div className="field">
          <label htmlFor="password">{mode === "reset" ? "Nova senha" : "Senha"}</label>
          <input
            id="password"
            name="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={mode === "login" ? undefined : 8}
            autoComplete={mode === "login" ? "current-password" : "new-password"}
            {...fields.props("password")}
          />
          <FieldError field="password" errors={fields.errors} />
          {mode !== "login" && <span className="minusculo">Mínimo de 8 caracteres.</span>}
        </div>
        )}

        {mode === "register" && (
          <div className="field">
            <label htmlFor="crn">CRN (optional)</label>
            <input
              id="crn"
              name="crn"
              value={crn}
              onChange={(e) => setCrn(e.target.value)}
              {...fields.props("crn")}
            />
            <FieldError field="crn" errors={fields.errors} />
          </div>
        )}

        <button className="button" type="submit" disabled={sending}>
          {sending ? "Aguarde…" : ACTIONS[mode]}
        </button>

        {mode === "login" && (
          <button
            type="button"
            className="link minusculo"
            style={{ alignSelf: "center" }}
            onClick={() => {
              setMode("recover");
              setError(null);
              setWarning(null);
            }}
          >
            Esqueci minha senha
          </button>
        )}

        <button
          type="button"
          className="button secundario"
          style={{ justifyContent: "center" }}
          onClick={() => {
            setMode(mode === "register" ? "login" : mode === "login" ? "register" : "login");
            setError(null);
            setWarning(null);
          }}
        >
          {mode === "register" ? "Já tenho conta" : mode === "login" ? "Criar uma conta" : "Voltar"}
        </button>
      </form>
      </div>
    </div>
  );
}

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Eye, UserPlus, X } from "lucide-react";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { FieldError, useFieldErrors } from "../components/FieldError";
import { useFeedback } from "../components/Feedback";
import { Avatar } from "../components/Avatar";
import { formatBr } from "../api/dates";
import type { AccountUser } from "../api/types";
import { count } from "../text";

/**
 * The practice's team.
 *
 * It exists so that the nutritionist does not lend their own password to the
 * receptionist — which is what happens in a practice without this, and is worse
 * than any permission flaw: a shared login makes the audit record useless,
 * because every action ends up in the owner's name.
 */
export default function Team() {
  const [users, setUsers] = useState<AccountUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setUsers(await api.users.list());
    } catch (e) {
      setError(explainError(e, "abrir a equipe"));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function toggle(u: AccountUser) {
    try {
      if (u.active) {
        if (!confirm(`Desativar ${u.name}? A sessão aberta dela cai na hora.`)) return;
        await api.users.deactivate(u.id);
      } else {
        await api.users.reactivate(u.id);
      }
      await load();
    } catch (e) {
      setError(explainError(e, "alterar o usuário"));
    }
  }

  return (
    <>
      <div className="header-page">
        <div>
          <h1>Equipe</h1>
          <p>{count(users.length, "pessoa com acesso", "pessoas com acesso")}</p>
        </div>
        <div className="header-page-actions">
          <button
            className={creating ? "button secundario" : "button"}
            onClick={() => setCreating((v) => !v)}
          >
            {creating ? <X aria-hidden="true" /> : <UserPlus aria-hidden="true" />}
            {creating ? "Cancelar" : "Cadastrar secretária"}
          </button>
        </div>
      </div>

      {error && (
        <div className="warning error mb-3" role="alert">
          {error}
        </div>
      )}

      {creating && (
        <Form
          onClose={() => setCreating(false)}
          onCreate={async () => {
            setCreating(false);
            await load();
          }}
        />
      )}

      {loading ? (
        <p className="loading">Carregando…</p>
      ) : (
        <div className="team-rows">
          {users.map((u) => (
            <div className={`team-row ${u.active ? "" : "inativo"}`} key={u.id}>
              <Avatar name={u.name} size={36} />
              <div className="team-body">
                <strong className="team-name">{u.name}</strong>
                <span className="team-email discreto">{u.email}</span>
              </div>
              <div className="tags">
                <span className={`tag ${u.role === "NUTRITIONIST" ? "verde" : ""}`}>
                  {u.roleDescription}
                </span>
                {!u.active && <span className="tag vermelha">inativo</span>}
              </div>
              <span className="team-since mono">Desde {formatBr(u.createdAt?.slice(0, 10))}</span>
              <div className="team-action">
                {u.role !== "NUTRITIONIST" && (
                  <button
                    className={`button ${u.active ? "perigo" : "secundario"} pequeno`}
                    onClick={() => toggle(u)}
                  >
                    {u.active ? "Desativar" : "Reativar"}
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      <aside className="library-note">
        <Eye aria-hidden="true" />
        <div>
          <h2>O que a secretária vê</h2>
          <p>
            Agenda e cadastro de pacientes. Não vê prescrição, antropometria, exames, receitas,
            orientações nem financeiro — prescrever é ato privativo do nutricionista, e o resto é
            dado clínico ou do dono da conta.
          </p>
        </div>
      </aside>
    </>
  );
}

function Form({
  onClose,
  onCreate,
}: {
  onClose: () => void;
  onCreate: () => Promise<void>;
}) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [phone, setPhone] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  const fields = useFieldErrors();
  const feedback = useFeedback();

  async function send(event: FormEvent) {
    event.preventDefault();
    setError(null);
    fields.clear();
    setSending(true);
    try {
      await api.users.createAssistant({
        name: name.trim(),
        email: email.trim(),
        initialPassword: password,
        phone: phone.trim() || undefined,
      });
      feedback.confirm(`${name.trim()} já pode entrar com o e-mail e a senha combinada.`);
      await onCreate();
    } catch (e) {
      if (!fields.apply(e)) {
        setError(explainError(e, "cadastrar a secretária"));
      }
      setSending(false);
    }
  }

  return (
    <form className="card mb-3" onSubmit={send}>
      <div className="card-head">
        <h2 className="card-title">Cadastrar secretária</h2>
      </div>

      {error && <div className="warning error mb-3">{error}</div>}

      <div className="grid two">
        <div className="field">
          <label htmlFor="eq-nome">Nome</label>
          <input
            id="eq-nome"
            name="name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            {...fields.props("name")}
          />
          <FieldError field="name" errors={fields.errors} />
        </div>
        <div className="field">
          <label htmlFor="eq-email">E-mail</label>
          <input
            id="eq-email"
            name="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            {...fields.props("email")}
          />
          <FieldError field="email" errors={fields.errors} />
        </div>
        <div className="field">
          <label htmlFor="eq-senha">Senha inicial</label>
          <input
            id="eq-senha"
            name="initialPassword"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={8}
            {...fields.props("initialPassword")}
          />
          <FieldError field="initialPassword" errors={fields.errors} />
          <span className="field-hint">
            Combine pessoalmente. Ela pode trocar depois em “Esqueci minha senha”.
          </span>
        </div>
        <div className="field">
          <label htmlFor="eq-tel">Telefone</label>
          <input
            id="eq-tel"
            name="phone"
            value={phone}
            onChange={(e) => setPhone(e.target.value)}
            {...fields.props("phone")}
          />
          <FieldError field="phone" errors={fields.errors} />
        </div>
      </div>

      <div className="row end mt-3">
        <button type="button" className="button secundario" onClick={onClose}>
          Cancelar
        </button>
        <button className="button" type="submit" disabled={sending}>
          {sending ? "Salvando…" : "Cadastrar"}
        </button>
      </div>
    </form>
  );
}

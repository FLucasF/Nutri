import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { Plus, Upload, Users } from "lucide-react";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { FieldError, useFieldErrors } from "../components/FieldError";
import { useFeedback } from "../components/Feedback";
import type { PatientSummary, PatientTag, ResultImport } from "../api/types";
import { count, plural } from "../text";
import { Avatar } from "../components/Avatar";
import { useIsNarrow } from "../hooks/useMediaQuery";

export default function Patients() {
  const navigate = useNavigate();
  const narrow = useIsNarrow();

  const [patients, setPatients] = useState<PatientSummary[]>([]);
  const [total, setTotal] = useState(0);
  const [term, setTerm] = useState("");
  const [activeOnly, setActiveOnly] = useState(true);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [panel, setPanel] = useState<"none" | "novo" | "importAll">("none");

  const find = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const page = await api.patients.list({
        term: term.trim() || undefined,
        active: activeOnly ? true : undefined,
        size: 50,
      });
      setPatients(page.content);
      setTotal(page.totalElements);
    } catch (e) {
      setError(explainError(e, "abrir a lista de pacientes"));
    } finally {
      setLoading(false);
    }
  }, [term, activeOnly]);

  // Search with a delay so as not to fire one query per keystroke.
  useEffect(() => {
    const clock = setTimeout(find, term ? 300 : 0);
    return () => clearTimeout(clock);
  }, [find, term]);

  return (
    <div className="patients-page">
      <div className="header-page">
        <div>
          <h1>Pacientes</h1>
          <p>
            {count(total, "paciente", "pacientes")}
            {activeOnly ? plural(total, " ativo", " ativos") : " no total"}
          </p>
        </div>
        <div className="header-page-actions">
          <button
            type="button"
            className="button secundario"
            aria-expanded={panel === "importAll"}
            onClick={() => setPanel(panel === "importAll" ? "none" : "importAll")}
          >
            <Upload aria-hidden="true" />
            Importar planilha
          </button>
          <button
            type="button"
            className="button"
            aria-expanded={panel === "novo"}
            onClick={() => setPanel(panel === "novo" ? "none" : "novo")}
          >
            <Plus aria-hidden="true" />
            Novo paciente
          </button>
        </div>
      </div>

      <div className="patients-body">
        {panel === "importAll" && (
          <PanelImport onClose={() => setPanel("none")} onImport={find} />
        )}
        {panel === "novo" && (
          <FormNovoPatient
            onClose={() => setPanel("none")}
            onCreate={(id) => navigate(`/patients/${id}`)}
          />
        )}

        <div className="card patients-toolbar">
          <div className="field grow">
            <label htmlFor="search">Buscar</label>
            <div className="input-search">
              <input
                id="search"
                type="search"
                value={term}
                onChange={(e) => setTerm(e.target.value)}
                placeholder="Nome, e-mail ou telefone"
                autoComplete="off"
              />
            </div>
          </div>
          <label className="patients-filter">
            <input
              type="checkbox"
              checked={activeOnly}
              onChange={(e) => setActiveOnly(e.target.checked)}
            />
            <span>Somente ativos</span>
          </label>
        </div>

        {error && (
          <div className="warning error" role="alert">
            {error}
          </div>
        )}

        {loading ? (
          <p className="loading">Carregando…</p>
        ) : patients.length === 0 ? (
          <div className="card empty">
            <span className="empty-icon">
              <Users aria-hidden="true" />
            </span>
            {term ? (
              <>
                <span className="empty-title">Nenhum paciente encontrado para "{term}".</span>
                <span className="empty-hint">Tente parte do nome, do e-mail ou do telefone.</span>
              </>
            ) : (
              <>
                <span className="empty-title">Nenhum paciente ainda.</span>
                <span className="empty-hint">Use Novo paciente para cadastrar o primeiro.</span>
              </>
            )}
          </div>
        ) : narrow ? (
          <PatientRows patients={patients} onOpen={(id) => navigate(`/patients/${id}`)} />
        ) : (
          <PatientTable patients={patients} onOpen={(id) => navigate(`/patients/${id}`)} />
        )}
      </div>
    </div>
  );
}

type ListProps = { patients: PatientSummary[]; onOpen: (id: number) => void };

/** The list on the desk: one row per patient, the whole row opens the record. */
function PatientTable({ patients, onOpen }: ListProps) {
  return (
    <div className="rolagem">
      <table className="patients-table">
        <colgroup>
          <col />
          <col className="col-email" />
          <col className="col-age" />
          <col className="col-status" />
        </colgroup>
        <thead>
          <tr>
            <th>Nome</th>
            <th>E-mail</th>
            <th className="num">Idade</th>
            <th>Situação</th>
          </tr>
        </thead>
        <tbody>
          {patients.map((p) => (
            <tr key={p.id} className="clicavel" onClick={() => onOpen(p.id)}>
              <td>
                <div className="linha-paciente">
                  <Avatar name={p.name} />
                  <div className="linha-paciente-texto">
                    <strong>{p.name}</strong>
                    {p.tags.length > 0 && <PatientTagList tags={p.tags} />}
                  </div>
                </div>
              </td>
              <td className="discreto">{p.email ?? "—"}</td>
              <td className="num">{p.age ?? "—"}</td>
              <td>
                <StatusTag active={p.active} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** The same list on the phone: rows instead of columns, nothing clipped. */
function PatientRows({ patients, onOpen }: ListProps) {
  return (
    <div className="list-rows patients-list">
      {patients.map((p) => (
        <button type="button" key={p.id} className="list-row" onClick={() => onOpen(p.id)}>
          <span className="list-row-lead">
            <Avatar name={p.name} size={36} />
          </span>
          <span className="list-row-body">
            <span className="list-row-title">{p.name}</span>
            {p.tags.length > 0 && <PatientTagList tags={p.tags} />}
            <span className="list-row-meta">{p.email ?? "—"}</span>
          </span>
          <span className="list-row-trail">
            <span className="patients-age">{p.age !== undefined ? `${p.age} anos` : "—"}</span>
            <StatusTag active={p.active} />
          </span>
        </button>
      ))}
    </div>
  );
}

function PatientTagList({ tags }: { tags: string[] }) {
  return (
    <span className="tags-paciente">
      {tags.map((tag) => (
        <span className="tag tag-paciente" key={tag}>
          {tag}
        </span>
      ))}
    </span>
  );
}

function StatusTag({ active }: { active: boolean }) {
  return <span className={active ? "tag verde" : "tag neutra"}>{active ? "ativo" : "inativo"}</span>;
}

/**
 * Importing patients from a spreadsheet.
 *
 * It is the first obstacle for whoever adopts the system: someone who already
 * sees patients has them in a spreadsheet or in another system, and typing two
 * hundred by hand is not an option. Without the patients, nothing else in the
 * product can be tried out.
 */
function PanelImport({
  onClose,
  onImport,
}: {
  onClose: () => void;
  onImport: () => void;
}) {
  const input = useRef<HTMLInputElement>(null);
  const [separator, setSeparator] = useState(",");
  const [result, setResult] = useState<ResultImport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  async function send(event: FormEvent) {
    event.preventDefault();
    const file = input.current?.files?.[0];
    if (!file) {
      setError("Escolha um arquivo CSV.");
      return;
    }
    setError(null);
    setResult(null);
    setSending(true);
    try {
      const output = await api.patients.importAll(file, separator);
      setResult(output);
      onImport();
    } catch (e) {
      setError(explainError(e, "importar o arquivo"));
    } finally {
      setSending(false);
    }
  }

  return (
    <form className="card patients-panel" onSubmit={send}>
      <div className="card-head">
        <div>
          <h2 className="card-title">Importar planilha de pacientes</h2>
          <p className="card-sub">
            Só a coluna <code>nome</code> é obrigatória. São reconhecidas também{" "}
            <code>email</code>, <code>telefone</code>, <code>nascimento</code>, <code>sexo</code>,{" "}
            <code>cpf</code>, <code>profissao</code>, <code>objetivo</code> e{" "}
            <code>observacoes</code> — sem diferenciar acento nem maiúscula. Uma linha com problema
            é anotada e as outras entram.
          </p>
        </div>
      </div>

      <div className="stack">
        {error && (
          <div className="warning error" role="alert">
            {error}
          </div>
        )}

        {result && (
          <div className={`warning ${result.imported > 0 ? "ok" : "attention"}`}>
            <strong>
              {count(result.imported, "paciente importado", "pacientes importados")}
              {result.ignored > 0
                ? `, ${count(result.ignored, "linha ignorada", "linhas ignoradas")}`
                : ""}
              .
            </strong>
            {result.warnings.length > 0 && (
              <ul className="problems-list">
                {result.warnings.slice(0, 8).map((warning, i) => (
                  <li key={i}>{warning}</li>
                ))}
              </ul>
            )}
          </div>
        )}

        <div className="patients-import-fields">
          <div className="field grow">
            <label htmlFor="imp-pac-arquivo">Arquivo CSV</label>
            <input id="imp-pac-arquivo" type="file" accept=".csv,text/csv" ref={input} />
          </div>
          <div className="field patients-import-sep">
            <label htmlFor="imp-pac-sep">Separador</label>
            <select
              id="imp-pac-sep"
              value={separator}
              onChange={(e) => setSeparator(e.target.value)}
            >
              <option value=",">Vírgula</option>
              <option value=";">Ponto e vírgula</option>
              <option value={"	"}>Tabulação</option>
            </select>
          </div>
        </div>

        <div className="row end">
          <button type="button" className="button secundario" onClick={onClose}>
            Fechar
          </button>
          <button className="button" type="submit" disabled={sending}>
            <Upload aria-hidden="true" />
            {sending ? "Importando…" : "Importar"}
          </button>
        </div>
      </div>
    </form>
  );
}

function FormNovoPatient({
  onClose,
  onCreate,
}: {
  onClose: () => void;
  onCreate: (id: number) => void;
}) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [dateBirth, setDateBirth] = useState("");
  const [sex, setSex] = useState("");
  const [goal, setGoal] = useState("");
  /**
   * CPF, apelido e TAGs entram já no cadastro.
   *
   * Existiam só na ficha, e o cliente cadastrava, abria a ficha e completava.
   * "Acrescentar todas as informações possíveis do WebDiet no NutriPlan: CPF,
   * apelido, TAG" — o WebDiet pede tudo de uma vez, e é assim que ele espera.
   */
  const [cpf, setCpf] = useState("");
  const [nickname, setNickname] = useState("");
  const [tags, setTags] = useState<PatientTag[]>([]);
  const [chosenTags, setChosenTags] = useState<number[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const fields = useFieldErrors();
  const feedback = useFeedback();

  useEffect(() => {
    api.patients
      .tags()
      .then(setTags)
      .catch(() => setTags([]));
  }, []);

  async function createTag() {
    const tagName = window.prompt("Nome da nova TAG:");
    if (!tagName?.trim()) return;
    try {
      const tag = await api.patients.createTag(tagName.trim());
      setTags(await api.patients.tags());
      setChosenTags((current) => [...current, tag.id]);
    } catch (e) {
      setError(explainError(e, "criar a TAG"));
    }
  }

  async function send(event: FormEvent) {
    event.preventDefault();
    setError(null);
    fields.clear();
    setSending(true);
    try {
      const created = await api.patients.create({
        name: name.trim(),
        email: email.trim() || undefined,
        phone: phone.trim() || undefined,
        dateBirth: dateBirth || undefined,
        sex: (sex || undefined) as never,
        cpf: cpf.trim() || undefined,
        nickname: nickname.trim() || undefined,
        goal: goal.trim() || undefined,
      });
      // As TAGs são um vínculo à parte do cadastro; o paciente já existe
      // quando elas são marcadas, e falhar aqui não desfaz o cadastro.
      if (chosenTags.length > 0) {
        try {
          await api.patients.setTags(created.id, chosenTags);
        } catch (e) {
          feedback.warn(e, "marcar as TAGs do paciente");
        }
      }
      feedback.confirm(`${created.name} entrou na sua lista de pacientes.`);
      onCreate(created.id);
    } catch (e) {
      // A field error sticks to the field; the rest goes to the strip, with the
      // sentence that says what was being attempted. Showing both would repeat
      // the information.
      if (!fields.apply(e)) {
        setError(explainError(e, "cadastrar o paciente"));
      }
      setSending(false);
    }
  }

  return (
    <form className="card patients-panel" onSubmit={send}>
      <div className="card-head">
        <h2 className="card-title">Novo paciente</h2>
      </div>

      <div className="stack">
        {error && (
          <div className="warning error" role="alert">
            {error}
          </div>
        )}

        <div className="grid two">
          <div className="field">
            <label htmlFor="np-nome">Nome</label>
            <input
              id="np-nome"
              name="name"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              autoFocus
              {...fields.props("name")}
            />
            <FieldError field="name" errors={fields.errors} />
          </div>
          <div className="field">
            <label htmlFor="np-email">E-mail</label>
            <input
              id="np-email"
              name="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              {...fields.props("email")}
            />
            <FieldError field="email" errors={fields.errors} />
          </div>
          <div className="field">
            <label htmlFor="np-tel">Telefone</label>
            <input
              id="np-tel"
              name="phone"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              {...fields.props("phone")}
            />
            <FieldError field="phone" errors={fields.errors} />
          </div>
          <div className="field">
            <label htmlFor="np-nasc">Data de nascimento</label>
            <input
              id="np-nasc"
              type="date"
              value={dateBirth}
              max={new Date().toISOString().slice(0, 10)}
              onChange={(e) => setDateBirth(e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="np-sexo">Sexo</label>
            <select id="np-sexo" value={sex} onChange={(e) => setSex(e.target.value)}>
              <option value="">Não informado</option>
              <option value="FEMALE">Feminino</option>
              <option value="MALE">Masculino</option>
            </select>
          </div>
          <div className="field">
            <label htmlFor="np-obj">Objetivo</label>
            <input id="np-obj" value={goal} onChange={(e) => setGoal(e.target.value)} />
          </div>
          <div className="field">
            <label htmlFor="np-cpf">CPF</label>
            <input
              id="np-cpf"
              name="cpf"
              inputMode="numeric"
              value={cpf}
              onChange={(e) => setCpf(e.target.value)}
              placeholder="000.000.000-00"
              {...fields.props("cpf")}
            />
            <FieldError field="cpf" errors={fields.errors} />
          </div>
          <div className="field">
            <label htmlFor="np-apelido">Apelido</label>
            <input
              id="np-apelido"
              name="nickname"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              placeholder="Como o paciente gosta de ser chamado"
              {...fields.props("nickname")}
            />
            <FieldError field="nickname" errors={fields.errors} />
          </div>
        </div>

        <div className="bloco-tags np-tags">
          <div className="bloco-tags-row">
            <span className="eyebrow">TAGs</span>
            {chosenTags.length === 0 && <span className="minusculo">nenhuma por enquanto</span>}
          </div>
          <div className="escolha-tags">
            {tags.map((tag) => {
              const on = chosenTags.includes(tag.id);
              return (
                <button
                  type="button"
                  key={tag.id}
                  className={`tag-escolha${on ? " ativa" : ""}`}
                  aria-pressed={on}
                  onClick={() =>
                    setChosenTags(on ? chosenTags.filter((x) => x !== tag.id) : [...chosenTags, tag.id])
                  }
                >
                  {tag.name}
                  {tag.own && <span className="sinal-sua" title="TAG do seu consultório" />}
                </button>
              );
            })}
            <button type="button" className="tag-escolha nova" onClick={() => void createTag()}>
              + nova TAG
            </button>
          </div>
        </div>

        <div className="row end">
          <button type="button" className="button secundario" onClick={onClose}>
            Cancelar
          </button>
          <button className="button" type="submit" disabled={sending}>
            {sending ? "Salvando…" : "Cadastrar"}
          </button>
        </div>
      </div>
    </form>
  );
}

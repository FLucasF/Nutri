import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { FieldError, useFieldErrors } from "../components/FieldError";
import { useFeedback } from "../components/Feedback";
import type { PatientSummary, ResultImport } from "../api/types";
import { count, plural } from "../text";

export default function Patients() {
  const navigate = useNavigate();

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
    <>
      <div className="header-page">
        <div>
          <h1>Patients</h1>
          <p>
            {count(total, "patient", "patients")}
            {activeOnly ? plural(total, " ativo", " ativos") : " no total"}
          </p>
        </div>
        <div className="row">
          <button
            className="button secundario"
            onClick={() => setPanel(panel === "importAll" ? "none" : "importAll")}
          >
            Importar planilha
          </button>
          <button
            className="button"
            onClick={() => setPanel(panel === "novo" ? "none" : "novo")}
          >
            Novo paciente
          </button>
        </div>
      </div>

      {panel === "importAll" && (
        <PanelImport onClose={() => setPanel("none")} onImport={find} />
      )}
      {panel === "novo" && (
        <FormNovoPatient
          onClose={() => setPanel("none")}
          onCreate={(id) => navigate(`/patients/${id}`)}
        />
      )}

      <div className="card" style={{ marginBottom: "0.9rem" }}>
        <div className="row">
          <div className="field" style={{ flex: 1, minWidth: 220 }}>
            <label htmlFor="search">Find</label>
            <input
              id="search"
              value={term}
              onChange={(e) => setTerm(e.target.value)}
              placeholder="Nome, e-mail ou telefone"
            />
          </div>
          <label className="row" style={{ gap: "0.4rem", paddingTop: "1.1rem" }}>
            <input
              type="checkbox"
              checked={activeOnly}
              onChange={(e) => setActiveOnly(e.target.checked)}
              style={{ width: "auto" }}
            />
            <span className="discreto">Somente ativos</span>
          </label>
        </div>
      </div>

      {error && (
        <div className="warning error" role="alert" style={{ marginBottom: "0.9rem" }}>
          {error}
        </div>
      )}

      {loading ? (
        <p className="loading">Loading…</p>
      ) : patients.length === 0 ? (
        <div className="card empty">
          {term
            ? `Nenhum paciente encontrado para "${term}". Tente parte do nome, do e-mail ou do telefone.`
            : "Nenhum paciente ainda. Use Novo paciente para cadastrar o primeiro."}
        </div>
      ) : (
        <div className="rolagem">
          <table>
            <thead>
              <tr>
                <th>Name</th>
                <th>E-mail</th>
                <th className="num">Age</th>
                <th>Situação</th>
              </tr>
            </thead>
            <tbody>
              {patients.map((p) => (
                <tr
                  key={p.id}
                  className="clicavel"
                  onClick={() => navigate(`/patients/${p.id}`)}
                >
                  <td>
                    <strong>{p.name}</strong>
                  </td>
                  <td className="discreto">{p.email ?? "—"}</td>
                  <td className="num">{p.age ?? "—"}</td>
                  <td>
                    <span className={`tag ${p.active ? "verde" : ""}`}>
                      {p.active ? "active" : "inactive"}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
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
    <form className="card" style={{ marginBottom: "0.9rem" }} onSubmit={send}>
      <h2>Importar planilha de pacientes</h2>
      <p className="discreto" style={{ margin: "0.3rem 0 0.9rem" }}>
        Só a coluna <code>name</code> é obrigatória. São reconhecidas também{" "}
        <code>email</code>, <code>phone</code>, <code>birth</code>, <code>sex</code>,{" "}
        <code>cpf</code>, <code>occupation</code>, <code>goal</code> e{" "}
        <code>notes</code> — sem diferenciar acento nem maiúscula. Uma linha com problema
        é anotada e as outras entram.
      </p>

      {error && (
        <div className="warning error" style={{ marginBottom: "0.85rem" }}>
          {error}
        </div>
      )}

      {result && (
        <div
          className={`warning ${result.imported > 0 ? "ok" : "attention"}`}
          style={{ marginBottom: "0.85rem" }}
        >
          <strong>
            {count(result.imported, "paciente importado", "pacientes importados")}
            {result.ignored > 0
              ? `, ${count(result.ignored, "linha ignorada", "linhas ignoradas")}`
              : ""}
            .
          </strong>
          {result.warnings.length > 0 && (
            <ul style={{ margin: "0.4rem 0 0", paddingLeft: "1.1rem" }}>
              {result.warnings.slice(0, 8).map((warning, i) => (
                <li key={i}>{warning}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      <div className="row">
        <div className="field" style={{ flex: 2, minWidth: 220 }}>
          <label htmlFor="imp-pac-arquivo">Arquivo CSV</label>
          <input id="imp-pac-arquivo" type="file" accept=".csv,text/csv" ref={input} />
        </div>
        <div className="field" style={{ width: 120 }}>
          <label htmlFor="imp-pac-sep">Separator</label>
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

      <div className="row end" style={{ marginTop: "0.9rem" }}>
        <button type="button" className="button secundario" onClick={onClose}>
          Close
        </button>
        <button className="button" type="submit" disabled={sending}>
          {sending ? "Importando…" : "Importar"}
        </button>
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
      const created = await api.patients.create({
        name: name.trim(),
        email: email.trim() || undefined,
        phone: phone.trim() || undefined,
        dateBirth: dateBirth || undefined,
        sex: (sex || undefined) as never,
        goal: goal.trim() || undefined,
      });
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
    <form className="card" style={{ marginBottom: "0.9rem" }} onSubmit={send}>
      <h2 style={{ marginBottom: "0.85rem" }}>Novo paciente</h2>

      {error && (
        <div className="warning error" role="alert" style={{ marginBottom: "0.85rem" }}>
          {error}
        </div>
      )}

      <div className="grid two">
        <div className="field">
          <label htmlFor="np-nome">Name</label>
          <input
            id="np-nome"
            name="name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
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
          <label htmlFor="np-tel">Phone</label>
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
          <label htmlFor="np-sexo">Sex</label>
          <select id="np-sexo" value={sex} onChange={(e) => setSex(e.target.value)}>
            <option value="">Não informado</option>
            <option value="FEMALE">Female</option>
            <option value="MALE">Male</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="np-obj">Goal</label>
          <input id="np-obj" value={goal} onChange={(e) => setGoal(e.target.value)} />
        </div>
      </div>

      <div className="row end" style={{ marginTop: "0.9rem" }}>
        <button type="button" className="button secundario" onClick={onClose}>
          Cancel
        </button>
        <button className="button" type="submit" disabled={sending}>
          {sending ? "Salvando…" : "Cadastrar"}
        </button>
      </div>
    </form>
  );
}

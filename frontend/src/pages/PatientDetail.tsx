import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { ErrorApi, api } from "../api/client";
import { explainError } from "../api/errors";
import { FieldError, useFieldErrors } from "../components/FieldError";
import { useFeedback } from "../components/Feedback";
import { formatBr } from "../api/dates";
import { Avatar } from "../components/Avatar";
import { PatientAttachments } from "../components/PatientAttachments";
import { PatientAppointment } from "../components/PatientAppointment";
import { WeightChart } from "../components/WeightChart";
import { PatientNotes, PatientTags } from "../components/PatientProfile";
import { NotesField, NotesView } from "../components/RichText/NotesField";
import type {
  Patient,
  PlanSummary,
  Questionnaire,
  QuestionnaireAnswer,
} from "../api/types";

export default function PatientDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const patientId = Number(id);

  const [patient, setPatient] = useState<Patient | null>(null);
  const [plans, setPlans] = useState<PlanSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [data, plansList] = await Promise.all([
        api.patients.find(patientId),
        api.prescriptions.list({ patientId, size: 50 }),
      ]);
      setPatient(data);
      setPlans(plansList.content);
    } catch (e) {
      setError(explainError(e, "abrir o paciente"));
    } finally {
      setLoading(false);
    }
  }, [patientId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function toggleStatus() {
    if (!patient) return;
    setSaving(true);
    try {
      if (patient.active) {
        await api.patients.deactivate(patient.id);
      } else {
        await api.patients.reactivate(patient.id);
      }
      await load();
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : "Falha ao alterar a situação.");
    } finally {
      setSaving(false);
    }
  }

  if (loading) return <p className="loading">Carregando…</p>;
  if (error && !patient) return <div className="warning error">{error}</div>;
  if (!patient) return null;

  return (
    <>
      <div className="header-page">
        <div>
          <Link to="/patients" className="minusculo">
            ← Pacientes
          </Link>
          <div className="row" style={{ alignItems: "center", gap: "0.7rem" }}>
            <Avatar name={patient.name} size={44} />
            <div>
              <h1 style={{ margin: "0.2rem 0 0" }}>{patient.name}</h1>
              <p style={{ margin: 0 }}>
                {patient.nickname ? `"${patient.nickname}" · ` : ""}
                {patient.age ? `${patient.age} anos` : "Idade não informada"}
                {patient.biologicalCondition === "PREGNANT" ? " · gestante" : ""}
                {patient.biologicalCondition === "LACTATING" ? " · lactante" : ""}
                {patient.goal ? ` · ${patient.goal}` : ""}
              </p>
            </div>
          </div>
          <PatientTags patientId={patient.id} />
        </div>
        <div className="row">
          {!patient.active && <span className="tag">inativo</span>}
          <button className="button secundario" onClick={() => setEditing((v) => !v)}>
            {editing ? "Cancelar edição" : "Editar"}
          </button>
          <PatientAppointment patientId={patient.id} />
          <Link className="button secundario" to={`/patients/${patient.id}/anamneses`}>
            Anamnese
          </Link>
          <Link className="button secundario" to={`/patients/${patient.id}/anthropometry`}>
            Antropometria
          </Link>
          <Link className="button secundario" to={`/patients/${patient.id}/energy`}>
            Cálculo energético
          </Link>
          <Link className="button secundario" to={`/patients/${patient.id}/labtests`}>
            Exames
          </Link>
          <button
            className="button"
            onClick={() => navigate(`/prescriptions/new?patientId=${patient.id}`)}
          >
            Nova prescrição
          </button>
        </div>
      </div>

      {error && (
        <div className="warning error" style={{ marginBottom: "0.9rem" }}>
          {error}
        </div>
      )}

      {editing ? (
        <FormEdit
          patient={patient}
          onSave={async (data) => {
            await api.patients.update(patient.id, data);
            setEditing(false);
            await load();
          }}
        />
      ) : (
        <div className="card" style={{ marginBottom: "1.1rem" }}>
          <div className="grid three">
            <Datum label="E-mail" value={patient.email} />
            <Datum label="Telefone" value={patient.phone} />
            <Datum label="Nascimento" value={formatDate(patient.dateBirth)} />
            <Datum
              label="Sexo"
              value={patient.sex === "FEMALE" ? "Feminino" : patient.sex === "MALE" ? "Masculino" : undefined}
            />
            <Datum label="CPF" value={patient.cpf} />
            <Datum label="Apelido" value={patient.nickname} />
            <Datum label="Profissão" value={patient.occupation} />
          </div>
          {patient.notes && (
            <div style={{ marginTop: "0.9rem" }}>
              <span className="minusculo">Observações</span>
              <div style={{ marginTop: "0.2rem", fontSize: "0.9rem" }}>
                <NotesView value={patient.notes} />
              </div>
            </div>
          )}
          <div className="row" style={{ marginTop: "1rem" }}>
            <button className="button secundario pequeno" onClick={toggleStatus} disabled={saving}>
              {patient.active ? "Inativar paciente" : "Reativar paciente"}
            </button>
            <span className="minusculo">
              Inativar preserva todo o histórico e prescrições.
            </span>
          </div>
        </div>
      )}

      <WeightChart patientId={patient.id} />

      <PatientNotes patientId={patient.id} />

      <PatientAttachments patientId={patient.id} />

      <QuestionnairesSection patientId={patient.id} />

      <h2 style={{ marginBottom: "0.6rem" }}>Prescrições</h2>
      {plans.length === 0 ? (
        <div className="card empty">
          Nenhuma prescrição para este paciente. Crie um plano para enviar o link a ele.
        </div>
      ) : (
        <div className="rolagem">
          <table>
            <thead>
              <tr>
                <th>Plano</th>
                <th>Situação</th>
                <th className="num">Refeições</th>
                <th className="num">kcal</th>
              </tr>
            </thead>
            <tbody>
              {plans.map((plan) => (
                <tr
                  key={plan.id}
                  className="clicavel"
                  onClick={() => navigate(`/prescriptions/${plan.id}`)}
                >
                  <td>
                    <strong>{plan.title}</strong>
                  </td>
                  <td>
                    <TagStatus status={plan.status} />
                  </td>
                  <td className="num">{plan.meals}</td>
                  <td className="num">
                    {plan.energyKcal ? Math.round(plan.energyKcal) : "—"}
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

export function TagStatus({ status }: { status: string }) {
  const classe = status === "ACTIVE" ? "verde" : status === "CLOSED" ? "" : "ambar";
  const text = status === "ACTIVE" ? "publicado" : status === "CLOSED" ? "encerrado" : "rascunho";
  return <span className={`tag ${classe}`}>{text}</span>;
}


/**
 * Questionnaires sent to this patient.
 *
 * Sending one before the appointment is what makes the consultation start from
 * the analysis, and not from the collection. The patient answers by link,
 * without an account — the same mechanism by which they read the plan.
 */
function QuestionnairesSection({ patientId }: { patientId: number }) {
  const [sendings, setSendings] = useState<QuestionnaireAnswer[]>([]);
  const [templates, setTemplates] = useState<Questionnaire[]>([]);
  const [chosen, setChosen] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState<number | null>(null);

  const load = useCallback(async () => {
    try {
      setSendings(await api.questionnaires.forPatient(patientId));
    } catch {
      setSendings([]);
    }
  }, [patientId]);

  useEffect(() => {
    void load();
    api.questionnaires.list().then(setTemplates).catch(() => setTemplates([]));
  }, [load]);

  async function send() {
    if (!chosen) return;
    setError(null);
    try {
      await api.questionnaires.send(patientId, { questionnaireId: Number(chosen) });
      setChosen("");
      await load();
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : "Falha ao enviar.");
    }
  }

  async function copy(sending: QuestionnaireAnswer) {
    const address = `${window.location.origin}/form/${sending.publicIdentifier}`;
    try {
      await navigator.clipboard.writeText(address);
      setCopied(sending.id);
      setTimeout(() => setCopied(null), 2000);
    } catch {
      setCopied(null);
    }
  }

  async function cancel(sending: QuestionnaireAnswer) {
    if (!confirm("Cancelar este envio? O link deixa de funcionar.")) return;
    try {
      await api.questionnaires.cancelSending(sending.id);
      await load();
    } catch (e) {
      setError(e instanceof ErrorApi ? e.message : "Falha ao cancelar.");
    }
  }

  return (
    <>
      <h2 style={{ marginBottom: "0.6rem" }}>Questionários</h2>

      {error && (
        <div className="warning error" style={{ marginBottom: "0.6rem" }}>
          {error}
        </div>
      )}

      <div className="card" style={{ marginBottom: "0.9rem" }}>
        <div className="row">
          <div className="field" style={{ flex: 1, minWidth: 220 }}>
            <label htmlFor="qz-modelo">Enviar questionário</label>
            <select
              id="qz-modelo"
              value={chosen}
              onChange={(e) => setChosen(e.target.value)}
            >
              <option value="">Escolher…</option>
              {templates.map((m) => (
                <option key={m.id} value={m.id}>
                  {m.name}
                </option>
              ))}
            </select>
          </div>
          <button
            type="button"
            className="button"
            onClick={send}
            disabled={!chosen}
            style={{ marginTop: "1.1rem" }}
          >
            Gerar link
          </button>
        </div>

        {sendings.length === 0 ? (
          <p className="minusculo" style={{ marginTop: "0.7rem", marginBottom: 0 }}>
            Nenhum enviado. O paciente responde pelo link, sem precisar de conta.
          </p>
        ) : (
          sendings.map((e) => (
            <div key={e.id} style={{ marginTop: "0.8rem" }}>
              <div className="row" style={{ gap: "0.5rem" }}>
                <strong style={{ fontSize: "0.92rem" }}>{e.questionnaire}</strong>
                <span className={`tag ${e.pending ? "ambar" : "verde"}`}>
                  {e.pending ? "aguardando resposta" : "respondido"}
                </span>
                {e.classification && <span className="tag">{e.classification}</span>}
                {e.score !== undefined && (
                  <span className="minusculo">escore {e.score}</span>
                )}
                <span style={{ flex: 1 }} />
                {e.pending ? (
                  <>
                    <button className="button secundario pequeno" onClick={() => copy(e)}>
                      {copied === e.id ? "Copiado!" : "Copiar link"}
                    </button>
                    <button className="button perigo pequeno" onClick={() => cancel(e)}>
                      Cancelar
                    </button>
                  </>
                ) : (
                  <span className="minusculo">
                    respondido em {formatBr(e.answeredAt?.slice(0, 10))}
                  </span>
                )}
              </div>

              {!e.pending && (
                <details style={{ marginTop: "0.4rem" }}>
                  <summary className="minusculo" style={{ cursor: "pointer" }}>
                    Ver respostas
                  </summary>
                  {e.items.map((i, n) => (
                    <div key={n} className="handout-attached" style={{ marginTop: "0.6rem" }}>
                      <h3>{i.question}</h3>
                      <p>{i.value}</p>
                    </div>
                  ))}
                </details>
              )}
            </div>
          ))
        )}
      </div>
    </>
  );
}

function Datum({ label, value }: { label: string; value?: string }) {
  return (
    <div>
      <span className="minusculo">{label}</span>
      <div style={{ fontSize: "0.92rem" }}>{value || "—"}</div>
    </div>
  );
}

function formatDate(iso?: string) {
  if (!iso) return undefined;
  const [year, month, day] = iso.split("-");
  return `${day}/${month}/${year}`;
}

function FormEdit({
  patient,
  onSave,
}: {
  patient: Patient;
  onSave: (data: Partial<Patient>) => Promise<void>;
}) {
  const [data, setData] = useState({
    name: patient.name,
    email: patient.email ?? "",
    phone: patient.phone ?? "",
    dateBirth: patient.dateBirth ?? "",
    sex: patient.sex ?? "",
    cpf: patient.cpf ?? "",
    nickname: patient.nickname ?? "",
    biologicalCondition: patient.biologicalCondition ?? "",
    occupation: patient.occupation ?? "",
    goal: patient.goal ?? "",
    notes: patient.notes ?? "",
  });
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const fields = useFieldErrors();
  const feedback = useFeedback();

  function change(field: keyof typeof data, value: string) {
    setData((current) => ({ ...current, [field]: value }));
  }

  async function send(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSending(true);
    try {
      await onSave({
        ...data,
        email: data.email || undefined,
        phone: data.phone || undefined,
        dateBirth: data.dateBirth || undefined,
        sex: (data.sex || undefined) as never,
        cpf: data.cpf || undefined,
        nickname: data.nickname || undefined,
        // Só faz sentido para mulher. O servidor descarta de qualquer forma;
        // não mandar evita a tela sugerir que faz.
        biologicalCondition:
          data.sex === "FEMALE"
            ? ((data.biologicalCondition || undefined) as never)
            : undefined,
        occupation: data.occupation || undefined,
        goal: data.goal || undefined,
        notes: data.notes || undefined,
      });
      feedback.confirm("Ficha atualizada.");
    } catch (e) {
      if (!fields.apply(e)) {
        setError(explainError(e, "salvar a ficha do paciente"));
      }
      setSending(false);
    }
  }

  return (
    <form className="card" style={{ marginBottom: "1.1rem" }} onSubmit={send}>
      {error && (
        <div className="warning error" style={{ marginBottom: "0.85rem" }}>
          {error}
        </div>
      )}
      <div className="grid two">
        <div className="field">
          <label htmlFor="ed-nome">Nome</label>
          <input id="ed-nome" value={data.name} onChange={(e) => change("name", e.target.value)} required name="name" {...fields.props("name")} />
          <FieldError field="name" errors={fields.errors} />
        </div>
        <div className="field">
          <label htmlFor="ed-email">E-mail</label>
          <input id="ed-email" type="email" value={data.email} onChange={(e) => change("email", e.target.value)} name="email" {...fields.props("email")} />
          <FieldError field="email" errors={fields.errors} />
        </div>
        <div className="field">
          <label htmlFor="ed-tel">Telefone</label>
          <input id="ed-tel" value={data.phone} onChange={(e) => change("phone", e.target.value)} name="phone" {...fields.props("phone")} />
          <FieldError field="phone" errors={fields.errors} />
        </div>
        <div className="field">
          <label htmlFor="ed-nasc">Nascimento</label>
          <input
            id="ed-nasc"
            type="date"
            value={data.dateBirth}
            max={new Date().toISOString().slice(0, 10)}
            onChange={(e) => change("dateBirth", e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="ed-sexo">Sexo</label>
          <select id="ed-sexo" value={data.sex} onChange={(e) => change("sex", e.target.value)}>
            <option value="">Não informado</option>
            <option value="FEMALE">Feminino</option>
            <option value="MALE">Masculino</option>
          </select>
        </div>
        <div className="field">
          <label htmlFor="ed-cpf">CPF</label>
          <input id="ed-cpf" value={data.cpf} onChange={(e) => change("cpf", e.target.value)} name="cpf" {...fields.props("cpf")} />
          <FieldError field="cpf" errors={fields.errors} />
        </div>
        <div className="field">
          <label htmlFor="ed-apelido">Apelido</label>
          <input
            id="ed-apelido"
            value={data.nickname}
            onChange={(e) => change("nickname", e.target.value)}
            maxLength={80}
          />
        </div>
        {data.sex === "FEMALE" && (
          <div className="field">
            <label htmlFor="ed-condicao">Condição biológica</label>
            <select
              id="ed-condicao"
              value={data.biologicalCondition}
              onChange={(e) => change("biologicalCondition", e.target.value)}
            >
              <option value="">Não gestante</option>
              <option value="PREGNANT">Gestante</option>
              <option value="LACTATING">Lactante</option>
            </select>
          </div>
        )}
        <div className="field">
          <label htmlFor="ed-prof">Profissão</label>
          <input id="ed-prof" value={data.occupation} onChange={(e) => change("occupation", e.target.value)} name="occupation" {...fields.props("occupation")} />
          <FieldError field="occupation" errors={fields.errors} />
        </div>
        <div className="field">
          <label htmlFor="ed-obj">Objetivo</label>
          <input id="ed-obj" value={data.goal} onChange={(e) => change("goal", e.target.value)} name="goal" {...fields.props("goal")} />
          <FieldError field="goal" errors={fields.errors} />
        </div>
      </div>
      <div style={{ marginTop: "0.85rem" }}>
        <NotesField
          label="Observações"
          value={data.notes ?? ""}
          onChange={(next) => change("notes", next)}
          minHeight="7rem"
          onDemand
        />
      </div>
      <div className="row end" style={{ marginTop: "0.9rem" }}>
        <button className="button" type="submit" disabled={sending}>
          {sending ? "Salvando…" : "Salvar alterações"}
        </button>
      </div>
    </form>
  );
}

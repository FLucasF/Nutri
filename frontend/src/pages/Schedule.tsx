import { useCallback, useEffect, useState, type FormEvent } from "react";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { FieldError, useFieldErrors } from "../componentes/FieldError";
import { useFeedback } from "../componentes/Feedback";
import { dayAbbreviated, formatBr, todayIso, weekStart, sumDays } from "../api/dates";
import type {
  Appointment,
  ScheduleDay,
  PatientSummary,
  AppointmentStatus,
  AppointmentType,
  TypeAppointmentInfo,
} from "../api/types";
import { count, plural } from "../text";

/** How the status is said in the confirmation, in the same vocabulary as the buttons. */
const STATUS_DESCRIPTION: Record<AppointmentStatus, string> = {
  SCHEDULED: "scheduled",
  CONFIRMED: "confirmed",
  COMPLETED: "completed",
  NOSHOW: "noshow",
  CANCELED: "canceled",
};

const CLASSE_BY_STATUS: Record<AppointmentStatus, string> = {
  SCHEDULED: "",
  CONFIRMED: "verde",
  COMPLETED: "verde",
  NOSHOW: "vermelha",
  CANCELED: "",
};

export default function Schedule() {
  const [day, setDay] = useState(todayIso());
  const [view, setView] = useState<"day" | "week">("day");
  const [schedule, setSchedule] = useState<ScheduleDay | null>(null);
  const [week, setWeek] = useState<Appointment[]>([]);
  const [patients, setPatients] = useState<PatientSummary[]>([]);
  const [types, setTypes] = useState<TypeAppointmentInfo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  const [subscribing, setSubscribing] = useState(false);
  const feedback = useFeedback();

  const weekFirst = weekStart(day);
  const weekLast = sumDays(weekFirst, 6);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      if (view === "week") {
        setWeek(await api.schedule.inRange(weekFirst, weekLast));
      } else {
        setSchedule(await api.schedule.forDay(day));
      }
    } catch (e) {
      setError(explainError(e, "abrir a agenda"));
    } finally {
      setLoading(false);
    }
  }, [day, view, weekFirst, weekLast]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    api.patients.list({ active: true, size: 200 })
      .then((p) => setPatients(p.content))
      .catch(() => setPatients([]));
    api.schedule.types().then(setTypes).catch(() => setTypes([]));
  }, []);

  async function changeStatus(id: number, status: AppointmentStatus) {
    setError(null);
    try {
      await api.schedule.changeStatus(id, status);
      feedback.confirm(`Atendimento marcado como ${STATUS_DESCRIPTION[status]}.`);
      await load();
    } catch (e) {
      setError(explainError(e, "alterar a situação do atendimento"));
    }
  }

  function move(days: number) {
    setDay(sumDays(day, days));
  }

  return (
    <>
      <div className="header-page">
        <div>
          <h1>Schedule</h1>
          <p>
            {view === "week"
              ? `${count(week.length, "appointment", "appointments")} · ${formatBr(weekFirst)} a ${formatBr(weekLast)}`
              : schedule
                ? [
                    count(schedule.appointmentsTotal, "appointment", "appointments"),
                    `${schedule.completed} ${plural(schedule.completed, "completed", "completed")}`,
                    ...(schedule.noshows > 0
                      ? [count(schedule.noshows, "noshow", "noshows")]
                      : []),
                  ].join(" · ")
                : "—"}
          </p>
        </div>
        <div className="row">
          <button
            className="button secundario"
            onClick={() => setSubscribing((v) => !v)}
            aria-expanded={subscribing}
          >
            Ver no meu calendário
          </button>
          <button className="button" onClick={() => setCreating((v) => !v)}>
            {creating ? "Cancelar" : "Novo atendimento"}
          </button>
        </div>
      </div>

      {subscribing && <ScheduleSubscription onClose={() => setSubscribing(false)} />}

      {error && <div className="warning error" style={{ marginBottom: "0.9rem" }}>{error}</div>}

      {creating && (
        <FormAppointment
          patients={patients}
          types={types}
          daySuggested={day}
          onSave={async () => {
            setCreating(false);
            await load();
          }}
        />
      )}

      <div className="card" style={{ marginBottom: "0.9rem" }}>
        <div className="row">
          <div className="picker-view" role="group" aria-label="Visão da agenda">
            <button
              type="button"
              aria-pressed={view === "day"}
              onClick={() => setView("day")}
            >
              Day
            </button>
            <button
              type="button"
              aria-pressed={view === "week"}
              onClick={() => setView("week")}
            >
              Week
            </button>
          </div>
          <button className="button secundario pequeno" onClick={() => move(view === "week" ? -7 : -1)}>
            {view === "week" ? "← Semana anterior" : "← Dia anterior"}
          </button>
          <div className="field" style={{ width: 170 }}>
            <input
              type="date"
              value={day}
              onChange={(e) => setDay(e.target.value)}
              aria-label="Data da agenda"
            />
          </div>
          <button className="button secundario pequeno" onClick={() => move(view === "week" ? 7 : 1)}>
            {view === "week" ? "Próxima semana →" : "Próximo dia →"}
          </button>
          <button
            className="button secundario pequeno"
            onClick={() => setDay(todayIso())}
          >
            Today
          </button>
          <span className="discreto">
            {view === "week"
              ? `${formatBr(weekFirst)} — ${formatBr(weekLast)}`
              : weekDay(day)}
          </span>
        </div>
      </div>

      {loading ? (
        <p className="loading">Loading…</p>
      ) : view === "week" ? (
        <ScheduleWeek
          start={weekFirst}
          appointments={week}
          onChooseDay={(d) => {
            setDay(d);
            setView("day");
          }}
          onChangeStatus={changeStatus}
        />
      ) : !schedule || schedule.appointments.length === 0 ? (
        <div className="card empty">
          Nenhum atendimento neste dia. Use <strong>Novo atendimento</strong> para marcar o
          primeiro.
        </div>
      ) : (
        /* The same ruler as the patient's plan: the day is a line, and the gap
           between two appointments shows up as a real gap. */
        <div className="ruler-day">
          {schedule.appointments.map((a) => (
            <AppointmentRow key={a.id} appointment={a} onChangeStatus={changeStatus} />
          ))}
        </div>
      )}
    </>
  );
}

/**
 * Schedule subscription in an external calendar.
 *
 * It is not an integration with the Google API: it is an address that Google
 * Calendar, Apple Calendar and Outlook subscribe to natively, and start
 * fetching on their own. It gives the professional what they want — seeing the
 * appointments in the calendar they already use — without asking them to
 * authorize an application.
 *
 * The way back does not exist: an event created in Google does not become an
 * appointment here. It is said on screen, because finding that out in practice
 * costs a lost slot.
 */
function ScheduleSubscription({ onClose }: { onClose: () => void }) {
  const [address, setAddress] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);
  const warnings = useFeedback();

  useEffect(() => {
    api.schedule
      .subscription()
      .then((a) => setAddress(a.token ? calendarUrl(a.token) : null))
      .catch((e) => setError(explainError(e, "ler a assinatura da agenda")))
      .finally(() => setLoading(false));
  }, []);

  function calendarUrl(token: string) {
    return `${window.location.origin}/api/publico/agenda/${token}.ics`;
  }

  async function generate() {
    setError(null);
    setCopied(false);
    try {
      const { token } = await api.schedule.generateSubscription();
      setAddress(calendarUrl(token));
    } catch (e) {
      setError(explainError(e, "gerar o endereço da assinatura"));
    }
  }

  async function turnOff() {
    if (!confirm("Desligar a assinatura? O calendário que já a usa para de receber a agenda.")) {
      return;
    }
    setError(null);
    try {
      await api.schedule.revokeSubscription();
      setAddress(null);
      warnings.confirm("Assinatura desligada. O calendário para de receber a agenda.");
    } catch (e) {
      setError(explainError(e, "desligar a assinatura"));
    }
  }

  async function copy() {
    if (!address) return;
    try {
      await navigator.clipboard.writeText(address);
      setCopied(true);
    } catch {
      // No clipboard permission: the address is on screen and can be copied
      // by hand, so this does not become an error.
      setCopied(false);
    }
  }

  return (
    <div className="card" style={{ marginBottom: "0.9rem" }}>
      <div className="row" style={{ justifyContent: "space-between" }}>
        <h2>Ver a agenda no meu calendário</h2>
        <button type="button" className="button secundario pequeno" onClick={onClose}>
          Close
        </button>
      </div>

      {error && (
        <div className="warning error" role="alert" style={{ margin: "0.6rem 0" }}>
          {error}
        </div>
      )}

      {loading ? (
        <p className="loading">Loading…</p>
      ) : !address ? (
        <>
          <p className="minusculo" style={{ marginTop: "0.5rem" }}>
            Gere um endereço e assine-o no Google Agenda, no Apple Calendar ou no Outlook. Eles
            passam a buscar a agenda sozinhos, e o que você marcar aqui aparece lá.
          </p>
          <div className="row" style={{ marginTop: "0.7rem" }}>
            <button type="button" className="button" onClick={generate}>
              Gerar endereço
            </button>
          </div>
        </>
      ) : (
        <>
          <div className="field" style={{ marginTop: "0.6rem" }}>
            <label htmlFor="endereco-da-agenda">Endereço da assinatura</label>
            <div className="row" style={{ gap: "0.4rem" }}>
              <input
                id="endereco-da-agenda"
                readOnly
                value={address}
                onFocus={(e) => e.currentTarget.select()}
                style={{ flex: 1, fontFamily: "inherit" }}
              />
              <button type="button" className="button secundario pequeno" onClick={copy}>
                {copied ? "Copiado" : "Copiar"}
              </button>
            </div>
            <span className="minusculo">
              No Google Agenda: <strong>Outros calendários → Do URL</strong>. No Apple Calendar:{" "}
              <strong>Arquivo → Nova assinatura de calendário</strong>.
            </span>
          </div>

          <p className="minusculo" style={{ marginTop: "0.7rem" }}>
            Quem tem este endereço vê a agenda com nome de paciente, sem senha — trate-o como uma
            chave. Gerar outro invalida este na hora.
          </p>
          <p className="minusculo" style={{ marginTop: "0.3rem" }}>
            O caminho é de mão única: um evento criado no seu calendário não vira atendimento aqui.
          </p>

          <div className="row" style={{ marginTop: "0.7rem" }}>
            <button type="button" className="button secundario pequeno" onClick={generate}>
              Gerar outro endereço
            </button>
            <button type="button" className="button perigo pequeno" onClick={turnOff}>
              TurnOff
            </button>
          </div>
        </>
      )}
    </div>
  );
}

/**
 * An appointment hanging from the day ruler.
 *
 * Extracted so that the week view could reuse it: the seven days use the same
 * row, and not a reduced version that would diverge over time.
 */
function AppointmentRow({
  appointment: a,
  onChangeStatus,
}: {
  appointment: Appointment;
  onChangeStatus: (id: number, status: AppointmentStatus) => void;
}) {
  return (
    <div className="ruler-item">
      <time className="ruler-hour" dateTime={a.start}>
        {hour(a.start)}
      </time>
      <div className="ruler-body">
        <div className="appointment">
          <div className="appointment-who">
            <strong>{a.patientName ?? "Sem paciente"}</strong>
            <span className={`tag ${CLASSE_BY_STATUS[a.status]}`}>
              {a.statusDescription}
            </span>
          </div>
          <p className="appointment-target">
            {a.typeDescription} · {hour(a.start)}–{hour(a.end)} · {a.durationMinutes} min
          </p>
          {a.notes && <p className="appointment-note">{a.notes}</p>}
          {a.reasonOutcome && <p className="appointment-note">{a.reasonOutcome}</p>}
          <Transitions appointment={a} onChoose={onChangeStatus} />
        </div>
      </div>
    </div>
  );
}

/**
 * The week as seven stacked days, each with its own ruler.
 *
 * Seven columns side by side would fit on a monitor and on no phone, and would
 * squeeze exactly what matters to read: the patient's name and the status.
 * Stacked, the empty day shows up too — and a hole in the schedule is
 * information.
 */
function ScheduleWeek({
  start,
  appointments,
  onChooseDay,
  onChangeStatus,
}: {
  start: string;
  appointments: Appointment[];
  onChooseDay: (day: string) => void;
  onChangeStatus: (id: number, status: AppointmentStatus) => void;
}) {
  const days = Array.from({ length: 7 }, (_, i) => sumDays(start, i));
  const today = todayIso();

  if (appointments.length === 0) {
    return (
      <div className="card empty">
        Nenhum atendimento entre {formatBr(start)} e {formatBr(sumDays(start, 6))}.
      </div>
    );
  }

  return (
    <div className="week">
      {days.map((d) => {
        const forDay = appointments.filter((a) => a.start.slice(0, 10) === d);
        return (
          <section className={`week-day ${d === today ? "today" : ""}`} key={d}>
            <header>
              <button type="button" onClick={() => onChooseDay(d)}>
                <span className="label-day">{dayAbbreviated(d)}</span>
                <span className="date-day">{formatBr(d).slice(0, 5)}</span>
              </button>
              <span className="minusculo">
                {forDay.length === 0 ? "free" : count(forDay.length, "appointment", "appointments")}
              </span>
            </header>
            {forDay.length > 0 && (
              <div className="ruler-day">
                {forDay.map((a) => (
                  <AppointmentRow key={a.id} appointment={a} onChangeStatus={onChangeStatus} />
                ))}
              </div>
            )}
          </section>
        );
      })}
    </div>
  );
}

/**
 * Only the transitions the server accepts from the current status.
 * Offering a button that would result in an error would be asking the
 * professional to discover the rule by trial.
 */
function Transitions({
  appointment,
  onChoose,
}: {
  appointment: Appointment;
  onChoose: (id: number, status: AppointmentStatus) => void;
}) {
  if (appointment.transitionsAllowed.length === 0) {
    return null;
  }
  return (
    <div className="row appointment-actions" style={{ gap: "0.3rem" }}>
      {appointment.transitionsAllowed.map((status) => (
        <button
          key={status}
          className={`button ${status === "COMPLETED" ? "" : "secundario"} pequeno`}
          onClick={() => onChoose(appointment.id, status)}
        >
          {statusLabel(status)}
        </button>
      ))}
    </div>
  );
}

function FormAppointment({
  patients,
  types,
  daySuggested,
  onSave,
}: {
  patients: PatientSummary[];
  types: TypeAppointmentInfo[];
  daySuggested: string;
  onSave: () => Promise<void>;
}) {
  const [patientId, setPatientId] = useState("");
  const [date, setDate] = useState(daySuggested);
  const [time, setTime] = useState("09:00");
  const [type, setType] = useState<AppointmentType>("FOLLOWUP");
  const [duration, setDuration] = useState("30");
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  /** On changing the type, the duration follows the suggestion — but stays editable. */
  function chooseType(novo: AppointmentType) {
    setType(novo);
    const info = types.find((t) => t.type === novo);
    if (info) setDuration(String(info.durationSuggestedMinutes));
  }

  const fields = useFieldErrors();
  const feedback = useFeedback();

  async function send(event: FormEvent) {
    event.preventDefault();
    setError(null);
    fields.clear();
    setSending(true);
    try {
      await api.schedule.schedule({
        patientId: Number(patientId),
        start: `${date}T${time}:00`,
        durationMinutes: Number(duration),
        type,
        notes: notes.trim() || undefined,
      });
      const who = patients.find((p) => String(p.id) === patientId)?.name ?? "Atendimento";
      feedback.confirm(`${who} marcado para ${formatBr(date)}, às ${time}.`);
      await onSave();
    } catch (e) {
      if (!fields.apply(e)) {
        setError(explainError(e, "marcar o atendimento"));
      }
      setSending(false);
    }
  }

  return (
    <form className="card" style={{ marginBottom: "0.9rem" }} onSubmit={send}>
      <h2>Novo atendimento</h2>

      {error && <div className="warning error" style={{ margin: "0.8rem 0" }}>{error}</div>}

      <div className="grid three" style={{ marginTop: "0.8rem" }}>
        <div className="field" style={{ gridColumn: "span 2" }}>
          <label htmlFor="ag-paciente">Patient</label>
          <select
            id="ag-paciente"
            name="patientId"
            value={patientId}
            onChange={(e) => setPatientId(e.target.value)}
            required
            {...fields.props("patientId")}
          >
            <option value="">Select…</option>
            {patients.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
          <FieldError field="patientId" errors={fields.errors} />
        </div>
        <div className="field">
          <label htmlFor="ag-tipo">Type</label>
          <select
            id="ag-tipo"
            value={type}
            onChange={(e) => chooseType(e.target.value as AppointmentType)}
          >
            {types.map((t) => (
              <option key={t.type} value={t.type}>
                {t.description}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="ag-data">Date</label>
          <input
            id="ag-data"
            name="start"
            type="date"
            value={date}
            onChange={(e) => setDate(e.target.value)}
            required
            {...fields.props("start")}
          />
          <FieldError field="start" errors={fields.errors} />
        </div>
        <div className="field">
          <label htmlFor="ag-hora">Início</label>
          <input
            id="ag-hora"
            type="time"
            value={time}
            onChange={(e) => setTime(e.target.value)}
            required
          />
        </div>
        <div className="field">
          <label htmlFor="ag-duracao">Duração (min)</label>
          <input
            id="ag-duracao"
            name="durationMinutes"
            inputMode="numeric"
            value={duration}
            onChange={(e) => setDuration(e.target.value)}
            required
            {...fields.props("durationMinutes")}
          />
          <FieldError field="durationMinutes" errors={fields.errors} />
        </div>
      </div>

      <div className="field" style={{ marginTop: "0.7rem" }}>
        <label htmlFor="ag-obs">Observação</label>
        <input
          id="ag-obs"
          name="notes"
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          {...fields.props("notes")}
        />
        <FieldError field="notes" errors={fields.errors} />
      </div>

      <div className="row end" style={{ marginTop: "0.9rem" }}>
        <button className="button" type="submit" disabled={sending}>
          {sending ? "Agendando…" : "Agendar"}
        </button>
      </div>
    </form>
  );
}

function statusLabel(status: AppointmentStatus) {
  const labels: Record<AppointmentStatus, string> = {
    SCHEDULED: "Reabrir",
    CONFIRMED: "Confirmar",
    COMPLETED: "Realizado",
    NOSHOW: "Faltou",
    CANCELED: "Cancelar",
  };
  return labels[status];
}

function hour(iso: string) {
  return iso.slice(11, 16);
}

function weekDay(iso: string) {
  const date = new Date(`${iso}T12:00:00`);
  return date.toLocaleDateString("pt-BR", { weekday: "long", day: "numeric", month: "long" });
}

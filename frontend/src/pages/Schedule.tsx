import { useCallback, useEffect, useState, type FormEvent } from "react";
import {
  CalendarCheck,
  CalendarDays,
  CalendarPlus,
  CalendarRange,
  CalendarSync,
  Check,
  ChevronLeft,
  ChevronRight,
  CircleCheck,
  Copy,
  Link,
  Plus,
  RotateCcw,
  UserX,
  X,
  type LucideIcon,
} from "lucide-react";
import { api } from "../api/client";
import { explainError } from "../api/errors";
import { FieldError, useFieldErrors } from "../components/FieldError";
import { useFeedback } from "../components/Feedback";
import { useIsCompact } from "../hooks/useMediaQuery";
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

/**
 * The colour of the status tag. Scheduled is the ordinary state and stays
 * neutral; confirmed is firm (info), completed is done (success), a no-show
 * is the one that costs a slot (danger), and a cancellation is struck through
 * on the card rather than coloured.
 */
const CLASSE_BY_STATUS: Record<AppointmentStatus, string> = {
  SCHEDULED: "neutra",
  CONFIRMED: "azul",
  COMPLETED: "verde",
  NOSHOW: "vermelha",
  CANCELED: "neutra",
};

/** The icon of each transition, for the week columns where the text has no room. */
const ICON_BY_STATUS: Record<AppointmentStatus, LucideIcon> = {
  SCHEDULED: RotateCcw,
  CONFIRMED: CalendarCheck,
  COMPLETED: CircleCheck,
  NOSHOW: UserX,
  CANCELED: X,
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

  const previousLabel = view === "week" ? "← Semana anterior" : "← Dia anterior";
  const nextLabel = view === "week" ? "Próxima semana →" : "Próximo dia →";

  // The appointment happening now (or the next one today) gets the marked
  // hour chip; on any other day nothing is "now".
  const currentId =
    view === "week" ? currentAppointmentId(week) : currentAppointmentId(schedule?.appointments ?? []);

  return (
    <div className="page-schedule">
      <div className="header-page">
        <div>
          <h1>Agenda</h1>
          <p>
            {view === "week"
              ? `${count(week.length, "atendimento", "atendimentos")} · ${formatBr(weekFirst)} a ${formatBr(weekLast)}`
              : schedule
                ? [
                    count(schedule.appointmentsTotal, "atendimento", "atendimentos"),
                    `${schedule.completed} ${plural(schedule.completed, "realizado", "realizados")}`,
                    ...(schedule.noshows > 0
                      ? [count(schedule.noshows, "falta", "faltas")]
                      : []),
                  ].join(" · ")
                : "—"}
          </p>
        </div>
        <div className="header-page-actions schedule-actions">
          <button
            type="button"
            className="button secundario"
            onClick={() => setSubscribing((v) => !v)}
            aria-expanded={subscribing}
          >
            <CalendarSync aria-hidden="true" />
            Ver no meu calendário
          </button>
          <button type="button" className="button" onClick={() => setCreating((v) => !v)}>
            {creating ? <X aria-hidden="true" /> : <Plus aria-hidden="true" />}
            {creating ? "Cancelar" : "Novo atendimento"}
          </button>
        </div>
      </div>

      {subscribing && <ScheduleSubscription onClose={() => setSubscribing(false)} />}

      {error && <div className="warning error mb-3">{error}</div>}

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

      <div className="card schedule-toolbar">
        <div className="segmented" role="group" aria-label="Visão da agenda">
          <button type="button" aria-pressed={view === "day"} onClick={() => setView("day")}>
            <CalendarDays aria-hidden="true" />
            Dia
          </button>
          <button type="button" aria-pressed={view === "week"} onClick={() => setView("week")}>
            <CalendarRange aria-hidden="true" />
            Semana
          </button>
        </div>

        <div className="date-stepper">
          <button
            type="button"
            className="button secundario icon"
            title={previousLabel}
            onClick={() => move(view === "week" ? -7 : -1)}
          >
            <ChevronLeft aria-hidden="true" />
            <span className="visually-hidden">{previousLabel}</span>
          </button>
          <input
            type="date"
            value={day}
            onChange={(e) => setDay(e.target.value)}
            aria-label="Data da agenda"
          />
          <button
            type="button"
            className="button secundario icon"
            title={nextLabel}
            onClick={() => move(view === "week" ? 7 : 1)}
          >
            <ChevronRight aria-hidden="true" />
            <span className="visually-hidden">{nextLabel}</span>
          </button>
        </div>
        <button type="button" className="button secundario" onClick={() => setDay(todayIso())}>
          Hoje
        </button>

        <span className="discreto toolbar-caption">
          {view === "week"
            ? `${formatBr(weekFirst)} — ${formatBr(weekLast)}`
            : weekDay(day)}
        </span>
      </div>

      {loading ? (
        <p className="loading">Carregando…</p>
      ) : view === "week" ? (
        <ScheduleWeek
          start={weekFirst}
          appointments={week}
          currentId={currentId}
          onChooseDay={(d) => {
            setDay(d);
            setView("day");
          }}
          onChangeStatus={changeStatus}
        />
      ) : !schedule || schedule.appointments.length === 0 ? (
        <div className="card empty">
          <span className="empty-icon">
            <CalendarDays aria-hidden="true" />
          </span>
          <span className="empty-title">Nenhum atendimento neste dia.</span>
          <span className="empty-hint">
            Use <strong>Novo atendimento</strong> para marcar o primeiro.
          </span>
        </div>
      ) : (
        /* The same ruler as the patient's plan: the day is a line, and the gap
           between two appointments shows up as a real gap. */
        <div className="ruler-day">
          {schedule.appointments.map((a) => (
            <AppointmentRow
              key={a.id}
              appointment={a}
              current={a.id === currentId}
              onChangeStatus={changeStatus}
            />
          ))}
        </div>
      )}
    </div>
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
    return `${window.location.origin}/api/public/schedule/${token}.ics`;
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
    <div className="card mb-3 schedule-subscription">
      <div className="card-head">
        <h2 className="card-title">Ver a agenda no meu calendário</h2>
        <button type="button" className="button ghost pequeno" onClick={onClose}>
          <X aria-hidden="true" />
          Fechar
        </button>
      </div>

      {error && (
        <div className="warning error mb-3" role="alert">
          {error}
        </div>
      )}

      {loading ? (
        <p className="loading">Carregando…</p>
      ) : !address ? (
        <div className="stack">
          <p className="discreto subscription-lead">
            Gere um endereço e assine-o no Google Agenda, no Apple Calendar ou no Outlook. Eles
            passam a buscar a agenda sozinhos, e o que você marcar aqui aparece lá.
          </p>
          <div className="row">
            <button type="button" className="button" onClick={generate}>
              <Link aria-hidden="true" />
              Gerar endereço
            </button>
          </div>
        </div>
      ) : (
        <div className="stack">
          <div className="field">
            <label htmlFor="endereco-da-agenda">Endereço da assinatura</label>
            <div className="copy-row">
              <input
                id="endereco-da-agenda"
                className="subscription-address"
                readOnly
                value={address}
                onFocus={(e) => e.currentTarget.select()}
              />
              <button type="button" className="button secundario" onClick={copy}>
                {copied ? <Check aria-hidden="true" /> : <Copy aria-hidden="true" />}
                {copied ? "Copiado" : "Copiar"}
              </button>
            </div>
            <span className="field-hint">
              No Google Agenda: <strong>Outros calendários → Do URL</strong>. No Apple Calendar:{" "}
              <strong>Arquivo → Nova assinatura de calendário</strong>.
            </span>
          </div>

          <div className="warning attention">
            Quem tem este endereço vê a agenda com nome de paciente, sem senha — trate-o como uma
            chave. Gerar outro invalida este na hora.
          </div>
          <p className="minusculo subscription-note">
            O caminho é de mão única: um evento criado no seu calendário não vira atendimento aqui.
          </p>

          <div className="row">
            <button type="button" className="button secundario pequeno" onClick={generate}>
              Gerar outro endereço
            </button>
            <button type="button" className="button perigo pequeno" onClick={turnOff}>
              Desligar
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

/**
 * An appointment hanging from the day ruler.
 *
 * Extracted so that the stacked week could reuse it: the seven days use the
 * same row, and not a reduced version that would diverge over time.
 */
function AppointmentRow({
  appointment: a,
  current,
  onChangeStatus,
}: {
  appointment: Appointment;
  current: boolean;
  onChangeStatus: (id: number, status: AppointmentStatus) => void;
}) {
  return (
    <div className="ruler-item">
      <time className={current ? "ruler-hour atual" : "ruler-hour"} dateTime={a.start}>
        {hour(a.start)}
      </time>
      <div className="ruler-body">
        <div className={a.status === "CANCELED" ? "appointment cancelado" : "appointment"}>
          <div className="appointment-who">
            <strong className="appointment-name">{a.patientName ?? "Sem paciente"}</strong>
            <span className={`tag ${CLASSE_BY_STATUS[a.status]}`}>{a.statusDescription}</span>
          </div>
          <p className="appointment-target">
            <span className="readout strong">
              {hour(a.start)}–{hour(a.end)}
            </span>
            <span className="appointment-type">{a.typeDescription}</span>
            <span className="readout">{a.durationMinutes} min</span>
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
 * An appointment inside a week column: the same facts as the ruler row, in
 * the width of one seventh of the screen. The actions become icon buttons,
 * each carrying its text as name and tooltip.
 */
function WeekAppointment({
  appointment: a,
  current,
  onChangeStatus,
}: {
  appointment: Appointment;
  current: boolean;
  onChangeStatus: (id: number, status: AppointmentStatus) => void;
}) {
  const classes = ["week-appt"];
  if (current) classes.push("atual");
  if (a.status === "CANCELED") classes.push("cancelado");
  return (
    <article className={classes.join(" ")}>
      <div className="week-appt-top">
        <time className="readout strong" dateTime={a.start}>
          {hour(a.start)}
        </time>
        <span className={`tag ${CLASSE_BY_STATUS[a.status]}`}>{a.statusDescription}</span>
      </div>
      <strong className="week-appt-name">{a.patientName ?? "Sem paciente"}</strong>
      <span className="week-appt-meta">
        {a.typeDescription} · {a.durationMinutes} min
      </span>
      {a.notes && (
        <p className="week-appt-note" title={a.notes}>
          {a.notes}
        </p>
      )}
      {a.reasonOutcome && (
        <p className="week-appt-note" title={a.reasonOutcome}>
          {a.reasonOutcome}
        </p>
      )}
      <Transitions appointment={a} onChoose={onChangeStatus} compact />
    </article>
  );
}

/**
 * The week: seven columns on a wide screen, seven stacked days below 900px.
 *
 * The columns give the overview a monitor has room for; stacked, each day
 * keeps the full ruler row, because a phone has no width to squeeze the
 * patient's name and the status into. Either way the empty day shows up too —
 * a hole in the schedule is information.
 */
function ScheduleWeek({
  start,
  appointments,
  currentId,
  onChooseDay,
  onChangeStatus,
}: {
  start: string;
  appointments: Appointment[];
  currentId: number | null;
  onChooseDay: (day: string) => void;
  onChangeStatus: (id: number, status: AppointmentStatus) => void;
}) {
  const days = Array.from({ length: 7 }, (_, i) => sumDays(start, i));
  const today = todayIso();
  const stacked = useIsCompact();

  if (appointments.length === 0) {
    return (
      <div className="card empty">
        <span className="empty-icon">
          <CalendarRange aria-hidden="true" />
        </span>
        <span className="empty-title">
          Nenhum atendimento entre {formatBr(start)} e {formatBr(sumDays(start, 6))}.
        </span>
      </div>
    );
  }

  return (
    <div className={stacked ? "week stacked" : "week columns"}>
      {days.map((d) => {
        const forDay = appointments.filter((a) => a.start.slice(0, 10) === d);
        return (
          <section className={`week-day ${d === today ? "today" : ""}`} key={d}>
            <header className="week-head">
              <button type="button" onClick={() => onChooseDay(d)}>
                <span className="label-day">{dayAbbreviated(d)}</span>
                <span className="date-day">{formatBr(d).slice(0, 5)}</span>
              </button>
              <span className="minusculo">
                {forDay.length === 0 ? "livre" : count(forDay.length, "atendimento", "atendimentos")}
              </span>
            </header>
            {stacked
              ? forDay.length > 0 && (
                  <div className="ruler-day">
                    {forDay.map((a) => (
                      <AppointmentRow
                        key={a.id}
                        appointment={a}
                        current={a.id === currentId}
                        onChangeStatus={onChangeStatus}
                      />
                    ))}
                  </div>
                )
              : (
                  <div className="week-list">
                    {forDay.map((a) => (
                      <WeekAppointment
                        key={a.id}
                        appointment={a}
                        current={a.id === currentId}
                        onChangeStatus={onChangeStatus}
                      />
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
 *
 * Compact (week columns): icon only, with the text as name and tooltip.
 */
function Transitions({
  appointment,
  onChoose,
  compact = false,
}: {
  appointment: Appointment;
  onChoose: (id: number, status: AppointmentStatus) => void;
  compact?: boolean;
}) {
  if (appointment.transitionsAllowed.length === 0) {
    return null;
  }
  return (
    <div className={compact ? "appointment-actions compact" : "appointment-actions"}>
      {appointment.transitionsAllowed.map((status) => {
        const label = statusLabel(status);
        const tone = status === "COMPLETED" ? "" : status === "CANCELED" ? "perigo" : "secundario";
        if (compact) {
          const Icon = ICON_BY_STATUS[status];
          return (
            <button
              key={status}
              type="button"
              className={`button ${tone} pequeno icon`}
              aria-label={label}
              title={label}
              onClick={() => onChoose(appointment.id, status)}
            >
              <Icon aria-hidden="true" />
            </button>
          );
        }
        return (
          <button
            key={status}
            type="button"
            className={`button ${tone} pequeno`}
            onClick={() => onChoose(appointment.id, status)}
          >
            {label}
          </button>
        );
      })}
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
    <form className="card mb-3 form-appointment" onSubmit={send}>
      <div className="card-head">
        <h2 className="card-title">Novo atendimento</h2>
      </div>

      {error && <div className="warning error mb-3">{error}</div>}

      <div className="stack">
        <div className="form-appointment-grid who">
          <div className="field">
            <label htmlFor="ag-paciente">Paciente</label>
            <select
              id="ag-paciente"
              name="patientId"
              value={patientId}
              onChange={(e) => setPatientId(e.target.value)}
              required
              {...fields.props("patientId")}
            >
              <option value="">Selecione…</option>
              {patients.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
            <FieldError field="patientId" errors={fields.errors} />
          </div>
          <div className="field">
            <label htmlFor="ag-tipo">Tipo</label>
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
        </div>

        <div className="form-appointment-grid when">
          <div className="field">
            <label htmlFor="ag-data">Data</label>
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

        <div className="field">
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

        <div className="row end">
          <button className="button" type="submit" disabled={sending}>
            <CalendarPlus aria-hidden="true" />
            {sending ? "Agendando…" : "Agendar"}
          </button>
        </div>
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

/** The local clock in the same shape as the appointments' start/end. */
function nowLocalIso(): string {
  const now = new Date();
  const hh = String(now.getHours()).padStart(2, "0");
  const mm = String(now.getMinutes()).padStart(2, "0");
  const ss = String(now.getSeconds()).padStart(2, "0");
  return `${todayIso()}T${hh}:${mm}:${ss}`;
}

/**
 * The appointment that is "now": the one in progress today or, failing that,
 * the next one still to start today. Cancelled ones do not count, and on any
 * other day there is none.
 */
function currentAppointmentId(appointments: Appointment[]): number | null {
  const now = nowLocalIso();
  const today = now.slice(0, 10);
  const ofToday = appointments
    .filter((a) => a.start.slice(0, 10) === today && a.status !== "CANCELED")
    .sort((x, y) => x.start.localeCompare(y.start));
  const inProgress = ofToday.find((a) => a.start <= now && now < a.end);
  const next = ofToday.find((a) => a.start > now);
  return (inProgress ?? next)?.id ?? null;
}

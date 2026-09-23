import { useEffect, useId, useState } from "react";
import { createPortal } from "react-dom";
import { CalendarPlus } from "lucide-react";

import { api } from "../api/client";
import { explainError } from "../api/errors";
import { todayIso } from "../api/dates";
import { useFeedback } from "../components/Feedback";
import type { AppointmentType, TypeAppointmentInfo } from "../api/types";

/**
 * Registrar consulta e agendar paciente, direto da ficha.
 *
 * "Deverá haver algumas funções fixas nesse menu... que serão os botões para
 * Registrar Consulta, que irá salvar que o paciente foi atendido ou irá ser
 * atendido... ou marcar uma data para a consulta dele, que irá conversar com as
 * funções de agenda do nutricionista e financeiro."
 *
 * São o mesmo objeto na agenda, e a diferença é só a situação com que nasce:
 * registrar é uma consulta que já aconteceu, agendar é uma que vai acontecer.
 * Por isso um formulário só, com dois botões — e não duas telas que gravariam
 * a mesma linha.
 *
 * O botão fica na linha de ações do cabeçalho; o formulário, quando aberto,
 * é levado por portal para `panelContainer` — um espaço logo abaixo do
 * cabeçalho — para não abrir no meio dos outros botões.
 */
export function PatientAppointment({
  patientId,
  onDone,
  panelContainer,
}: {
  patientId: number;
  onDone?: () => void;
  /** Where the open form is rendered. Without one it opens in place. */
  panelContainer?: HTMLElement | null;
}) {
  const feedback = useFeedback();
  const panelId = useId();
  const [types, setTypes] = useState<TypeAppointmentInfo[]>([]);
  const [open, setOpen] = useState(false);
  const [type, setType] = useState<AppointmentType | "">("");
  const [date, setDate] = useState(todayIso());
  const [time, setTime] = useState("09:00");
  const [duration, setDuration] = useState("60");
  const [notes, setNotes] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.schedule
      .types()
      .then((list) => {
        setTypes(list);
        const first = list[0];
        if (first) {
          setType(first.type);
          setDuration(String(first.durationSuggestedMinutes));
        }
      })
      .catch(() => setTypes([]));
  }, []);

  async function save(past: boolean) {
    if (!type) {
      setError("Escolha o tipo de atendimento.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const appointment = await api.schedule.schedule({
        patientId,
        start: `${date}T${time}:00`,
        durationMinutes: Number(duration),
        type,
        notes: notes.trim() || undefined,
      });
      // Registrar é gravar que já aconteceu. Agendar deixa como marcado.
      if (past) {
        await api.schedule.changeStatus(appointment.id, "COMPLETED");
      }
      feedback.confirm(
        past ? "Consulta registrada na agenda." : "Paciente agendado.",
      );
      setOpen(false);
      setNotes("");
      onDone?.();
    } catch (e) {
      setError(explainError(e, past ? "registrar a consulta" : "agendar o paciente"));
    } finally {
      setSaving(false);
    }
  }

  const panel = open && (
    <div className="card painel-consulta" id={panelId}>
      <div className="card-head">
        <h3 className="card-title">Consulta</h3>
      </div>
      <div className="stack">
        {error && (
          <div className="warning error" role="alert">
            {error}
          </div>
        )}

        <div className="grid two">
          <div className="field">
            <label htmlFor="co-tipo">Tipo</label>
            <select
              id="co-tipo"
              value={type}
              onChange={(e) => {
                const chosen = e.target.value as AppointmentType;
                setType(chosen);
                const info = types.find((t) => t.type === chosen);
                if (info) setDuration(String(info.durationSuggestedMinutes));
              }}
            >
              {types.map((t) => (
                <option key={t.type} value={t.type}>
                  {t.description}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="co-dur">Duração (min)</label>
            <input
              id="co-dur"
              inputMode="numeric"
              value={duration}
              onChange={(e) => setDuration(e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="co-data">Data</label>
            <input
              id="co-data"
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="co-hora">Hora</label>
            <input
              id="co-hora"
              type="time"
              value={time}
              onChange={(e) => setTime(e.target.value)}
            />
          </div>
        </div>

        <div className="field">
          <label htmlFor="co-obs">Observação</label>
          <input
            id="co-obs"
            type="text"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            maxLength={1000}
          />
        </div>

        <div className="consulta-footer">
          <div className="row">
            <button type="button" className="button" onClick={() => void save(true)} disabled={saving}>
              Registrar consulta
            </button>
            <button
              type="button"
              className="button secundario"
              onClick={() => void save(false)}
              disabled={saving}
            >
              Agendar
            </button>
            <button
              type="button"
              className="button ghost"
              onClick={() => setOpen(false)}
              disabled={saving}
            >
              Fechar
            </button>
          </div>
          <p className="field-hint">
            Registrar grava como atendida; agendar deixa marcada. As duas aparecem na
            agenda e podem gerar recibo no financeiro.
          </p>
        </div>
      </div>
    </div>
  );

  return (
    <>
      <button
        type="button"
        className="button secundario"
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        onClick={() => setOpen((v) => !v)}
      >
        <CalendarPlus aria-hidden="true" />
        Consulta
      </button>
      {panel && (panelContainer ? createPortal(panel, panelContainer) : panel)}
    </>
  );
}

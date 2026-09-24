import { useState, type FormEvent } from "react";
import { LockKeyhole } from "lucide-react";
import { api } from "../api/client";
import { explainError } from "../api/errors";

/** Onde a página guarda o passe do link, só enquanto a guia estiver aberta. */
export function planAccessKey(identifier: string) {
  return `nutriplan.plan.${identifier}`;
}

export function readPlanAccess(identifier: string): string | undefined {
  try {
    return sessionStorage.getItem(planAccessKey(identifier)) ?? undefined;
  } catch {
    return undefined;
  }
}

function storePlanAccess(identifier: string, token: string) {
  try {
    sessionStorage.setItem(planAccessKey(identifier), token);
  } catch {
    /* navegação privada: o passe vale só nesta visita */
  }
}

/**
 * A porta do link do plano: a data de nascimento antes do cardápio.
 *
 * O plano é do paciente, e o link pode ser encaminhado por engano. Como no
 * WebDiet, quem abre confirma a data de nascimento; a data certa vira um
 * passe que vale por doze horas nesta guia. A página não diz de quem é o
 * plano antes da data: o nome seria justamente o que ela protege.
 */
export function PlanGate({ identifier, onOpened }: { identifier: string; onOpened: (token: string) => void }) {
  const [birthDate, setBirthDate] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  async function send(event: FormEvent) {
    event.preventDefault();
    if (!birthDate) {
      setError("Informe a data de nascimento do paciente.");
      return;
    }
    setError(null);
    setSending(true);
    try {
      const { accessToken } = await api.publicPlanAccess(identifier, birthDate);
      storePlanAccess(identifier, accessToken);
      onOpened(accessToken);
    } catch (e) {
      setError(explainError(e, "abrir o plano"));
      setSending(false);
    }
  }

  return (
    <div className="page-patient">
      <div className="plan-patient plan-gate">
        <form className="card card-centered plan-gate-card" onSubmit={send}>
          <span className="empty-icon">
            <LockKeyhole aria-hidden="true" />
          </span>
          <h1>Seu plano alimentar</h1>
          <p className="discreto">
            Para proteger os seus dados, confirme a data de nascimento do paciente antes de abrir o
            plano.
          </p>
          {error && (
            <div className="warning error" role="alert">
              {error}
            </div>
          )}
          <div className="field plan-gate-field">
            <label htmlFor="plano-nascimento">Data de nascimento</label>
            <input
              id="plano-nascimento"
              type="date"
              value={birthDate}
              max={new Date().toISOString().slice(0, 10)}
              onChange={(e) => setBirthDate(e.target.value)}
              required
            />
          </div>
          <button className="button grande w-full" type="submit" disabled={sending}>
            {sending ? "Conferindo…" : "Abrir o plano"}
          </button>
        </form>
      </div>
    </div>
  );
}

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { CircleAlert, CircleCheck, X } from "lucide-react";
import { explainError, nowHour } from "../api/errors";

type Type = "ok" | "error";

type Feedback = { type: Type; text: string; hour: string; key: number };

type Panel = {
  /** Confirms that it worked. The hour comes in on its own. */
  confirm: (text: string) => void;
  /** Explains what prevented it, from the failure and from what was being attempted. */
  warn: (error: unknown, action: string) => void;
  clear: () => void;
};

const Context = createContext<Panel | null>(null);

/** How long the confirmation stays on screen before leaving on its own. */
const CONFIRMATION_DURATION = 6000;

export function FeedbackProvider({ children }: { children: ReactNode }) {
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const clock = useRef<number | undefined>(undefined);

  const scheduleOutput = useCallback((type: Type) => {
    window.clearTimeout(clock.current);
    // Only the confirmation leaves on its own. An error that disappears before
    // being read is an error that was not communicated — and the person is left
    // not knowing what to fix.
    if (type === "ok") {
      clock.current = window.setTimeout(() => setFeedback(null), CONFIRMATION_DURATION);
    }
  }, []);

  const panel = useMemo<Panel>(
    () => ({
      confirm: (text) => {
        setFeedback({ type: "ok", text, hour: nowHour(), key: Date.now() });
        scheduleOutput("ok");
      },
      warn: (error, action) => {
        setFeedback({
          type: "error",
          text: explainError(error, action),
          hour: nowHour(),
          key: Date.now(),
        });
        scheduleOutput("error");
      },
      clear: () => {
        window.clearTimeout(clock.current);
        setFeedback(null);
      },
    }),
    [scheduleOutput],
  );

  useEffect(() => () => window.clearTimeout(clock.current), []);

  return (
    <Context.Provider value={panel}>
      {children}
      <FeedbackBanner feedback={feedback} onClose={panel.clear} />
    </Context.Provider>
  );
}

export function useFeedback(): Panel {
  const panel = useContext(Context);
  if (!panel) {
    throw new Error("useRecado precisa estar dentro de ProvedorDeRecado");
  }
  return panel;
}

/**
 * The toast, anchored to the foot of the screen.
 *
 * An icon says which of the two it is before the text is read; the hour, in
 * the mono readout face, answers the question a person really asks after a
 * long edit — *did my last change go in?*.
 *
 * `role` changes with the type because a screen reader treats the two
 * differently: a confirmation waits for the sentence in progress to end, an
 * error interrupts.
 */
function FeedbackBanner({ feedback, onClose }: { feedback: Feedback | null; onClose: () => void }) {
  return (
    <div className="feedback-anchor">
      {/* The region always exists, even empty: a screen reader only announces
          changes in a region that was already in the document. */}
      <div aria-live="polite" aria-atomic="true" className="feedback-region">
        {feedback?.type === "ok" && (
          <div className="feedback-banner ok" key={feedback.key}>
            <span className="feedback-icon" aria-hidden="true">
              <CircleCheck size={20} />
            </span>
            <p className="feedback-text">{feedback.text}</p>
            <time className="feedback-hour">{feedback.hour}</time>
          </div>
        )}
      </div>
      <div aria-live="assertive" aria-atomic="true" className="feedback-region">
        {feedback?.type === "error" && (
          <div className="feedback-banner error" role="alert" key={feedback.key}>
            <span className="feedback-icon" aria-hidden="true">
              <CircleAlert size={20} />
            </span>
            <p className="feedback-text">{feedback.text}</p>
            <time className="feedback-hour">{feedback.hour}</time>
            <button
              type="button"
              className="feedback-close"
              onClick={onClose}
              aria-label="Fechar aviso"
              title="Fechar aviso"
            >
              <X size={16} aria-hidden="true" />
            </button>
          </div>
        )}
      </div>
    </div>
  );
}

import { useEffect, useId, useRef, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { X } from "lucide-react";

/**
 * The sheet: a panel that slides in over the page — the navigation drawer
 * from the left on tablet and phone, a bottom sheet for pickers on the phone.
 *
 * It owns the things a modal must not forget: a portal to <body>, a backdrop
 * that closes on click, Escape, focus moved in and returned on close, Tab kept
 * inside, and the page scroll locked while it is open (body.sheet-open, see
 * components.css). The styling is in the stylesheet; this file only holds the
 * behaviour.
 */
export type SheetProps = {
  open: boolean;
  onClose: () => void;
  /** Names the dialog for assistive technology; shown unless `hideTitle`. */
  title: string;
  side?: "left" | "bottom";
  /** Keep the title in the accessibility tree only (the nav drawer). */
  hideTitle?: boolean;
  /** Extra class on the panel, e.g. "drawer" for the navigation drawer. */
  className?: string;
  children: ReactNode;
};

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

export function Sheet({ open, onClose, title, side = "left", hideTitle = false, className, children }: SheetProps) {
  const panel = useRef<HTMLDivElement>(null);
  const titleId = useId();

  // The latest onClose, without re-running the effect (and re-focusing the
  // panel) every time the parent renders with a fresh function.
  const close = useRef(onClose);
  useEffect(() => {
    close.current = onClose;
  }, [onClose]);

  useEffect(() => {
    if (!open) return;
    const returnTo = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    document.body.classList.add("sheet-open");
    panel.current?.focus();

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.stopPropagation();
        close.current();
        return;
      }
      if (event.key !== "Tab" || !panel.current) return;
      const focusable = Array.from(panel.current.querySelectorAll<HTMLElement>(FOCUSABLE));
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (!first || !last) {
        event.preventDefault();
        return;
      }
      const active = document.activeElement;
      if (event.shiftKey && (active === first || active === panel.current)) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && active === last) {
        event.preventDefault();
        first.focus();
      }
    }

    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      document.body.classList.remove("sheet-open");
      returnTo?.focus();
    };
  }, [open]);

  if (!open) return null;

  const classes = ["sheet", side, className].filter(Boolean).join(" ");

  return createPortal(
    <div className="sheet-root">
      <div className="sheet-backdrop" onClick={() => close.current()} />
      <div
        ref={panel}
        className={classes}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
      >
        {side === "bottom" && <span className="sheet-handle" aria-hidden="true" />}
        <div className="sheet-head">
          <h2 className={hideTitle ? "sheet-title visually-hidden" : "sheet-title"} id={titleId}>
            {title}
          </h2>
          <button
            type="button"
            className="button icon ghost sheet-close"
            onClick={() => close.current()}
            aria-label="Fechar"
            title="Fechar"
          >
            <X size={20} aria-hidden="true" />
          </button>
        </div>
        <div className="sheet-body">{children}</div>
      </div>
    </div>,
    document.body,
  );
}

/**
 * Dates in the user's timezone.
 *
 * `toISOString()` converts to UTC before formatting. In Brazil (UTC−3), opening
 * the system after 9pm would make "today" become the next day — the schedule
 * would open tomorrow, and an assessment recorded at night would get a future
 * date, which the server refuses. These functions format from the local
 * components.
 */

export function toIso(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function todayIso(): string {
  return toIso(new Date());
}

export function firstMonthIsoDay(): string {
  const today = new Date();
  return toIso(new Date(today.getFullYear(), today.getMonth(), 1));
}

export function lastMonthIsoDay(): string {
  const today = new Date();
  return toIso(new Date(today.getFullYear(), today.getMonth() + 1, 0));
}

/** Adds days to an ISO date, returning another ISO date. */
export function sumDays(iso: string, days: number): string {
  const [year, month, day] = iso.split("-").map(Number);
  const date = new Date(year!, month! - 1, day! + days);
  return toIso(date);
}

export function formatBr(iso?: string): string {
  if (!iso) return "—";
  const [year, month, day] = iso.split("-");
  return `${day}/${month}/${year}`;
}

/**
 * The Monday of the week the date falls in.
 *
 * `getDay()` returns 0 for Sunday, and a practice's working week starts on
 * Monday — without the adjustment, the week of a Sunday would show the six
 * following days instead of the six preceding ones.
 */
export function weekStart(iso: string): string {
  const [year, month, day] = iso.split("-").map(Number);
  const date = new Date(year!, month! - 1, day!);
  const weekDay = (date.getDay() + 6) % 7;
  return sumDays(iso, -weekDay);
}

/** Adds months, clamping the day to the length of the month it lands in. */
export function addMonths(iso: string, months: number): string {
  const [year, month, day] = iso.split("-").map(Number);
  const first = new Date(year!, month! - 1 + months, 1);
  const last = new Date(first.getFullYear(), first.getMonth() + 1, 0).getDate();
  return toIso(new Date(first.getFullYear(), first.getMonth(), Math.min(day!, last)));
}

export function monthStart(iso: string): string {
  return `${iso.slice(0, 7)}-01`;
}

export function monthEnd(iso: string): string {
  const [year, month] = iso.split("-").map(Number);
  return toIso(new Date(year!, month!, 0));
}

/** "setembro de 2026" */
export function monthLabel(iso: string): string {
  const [year, month] = iso.split("-").map(Number);
  return new Date(year!, month! - 1, 1).toLocaleDateString("pt-BR", {
    month: "long",
    year: "numeric",
  });
}

/** Name of the day of the week, e.g. "seg". */
export function dayAbbreviated(iso: string): string {
  const [year, month, day] = iso.split("-").map(Number);
  return new Date(year!, month! - 1, day!)
    .toLocaleDateString("pt-BR", { weekday: "short" })
    .replace(".", "");
}

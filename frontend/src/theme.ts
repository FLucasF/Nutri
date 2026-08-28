/**
 * Theme choice, kept in the browser itself.
 *
 * There are three states and not two. A light/dark switch forces the user to
 * pin one of the two forever; "system" is the default and has to stay available
 * for whoever wants to go back to following the device — which switches on its
 * own at nightfall, on a good share of phones.
 *
 * The theme itself lives in the `data-theme` attribute of the root element,
 * read by the CSS together with `color-scheme`. Here only the choice is made.
 */
export type Theme = "system" | "light" | "dark";

/** Also used by the script in index.html, which applies the theme before the paint. */
export const KEY_THEME = "nutriplan.theme";

export const THEMES: { value: Theme; label: string }[] = [
  { value: "system", label: "Sistema" },
  { value: "light", label: "Claro" },
  { value: "dark", label: "Escuro" },
];

export function themeGuardado(): Theme {
  try {
    const saved = localStorage.getItem(KEY_THEME);
    return saved === "light" || saved === "dark" ? saved : "system";
  } catch {
    // Private browsing or blocked storage: it follows the system.
    return "system";
  }
}

export function applyTheme(theme: Theme): void {
  const root = document.documentElement;
  if (theme === "system") {
    root.removeAttribute("data-theme");
  } else {
    root.setAttribute("data-theme", theme);
  }
  try {
    if (theme === "system") {
      localStorage.removeItem(KEY_THEME);
    } else {
      localStorage.setItem(KEY_THEME, theme);
    }
  } catch {
    // Without persistence the choice holds only for this tab, which is better than failing.
  }
}

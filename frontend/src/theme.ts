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

/**
 * The browser chrome colour (address bar, PWA title bar) follows the theme the
 * app is actually showing, not only the device scheme: a user who pins "Escuro"
 * on a light OS would otherwise get a dark app under a light bar. The two
 * values repeat --canvas from tokens.css; the pre-paint script in index.html
 * repeats them too, for the same reason it repeats the key.
 */
const THEME_COLOR = { light: "#F6F5F2", dark: "#121411" } as const;
const DARK_SCHEME = "(prefers-color-scheme: dark)";

let current: Theme = "system";
let listening = false;

function effectiveScheme(theme: Theme): "light" | "dark" {
  if (theme !== "system") return theme;
  return typeof window.matchMedia === "function" && window.matchMedia(DARK_SCHEME).matches
    ? "dark"
    : "light";
}

function syncThemeColor(): void {
  document
    .querySelector('meta[name="theme-color"]:not([media])')
    ?.setAttribute("content", THEME_COLOR[effectiveScheme(current)]);
}

function listenToDevice(): void {
  // While the theme is "system", the device may switch at nightfall; the meta has to follow.
  if (listening || typeof window.matchMedia !== "function") return;
  listening = true;
  window.matchMedia(DARK_SCHEME).addEventListener("change", () => {
    if (current === "system") syncThemeColor();
  });
}

/**
 * Called once at start-up. The pre-paint script already set `data-theme` and
 * the meta; this takes over from there — the saved choice becomes the current
 * one and the device is followed while nothing is pinned.
 */
export function watchTheme(): void {
  current = themeGuardado();
  syncThemeColor();
  listenToDevice();
}

export function applyTheme(theme: Theme): void {
  const root = document.documentElement;
  current = theme;
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
  syncThemeColor();
  listenToDevice();
}

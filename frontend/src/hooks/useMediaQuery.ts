import { useCallback, useSyncExternalStore } from "react";

/**
 * Subscribes to a media query and re-renders when it flips.
 *
 * Used where the markup itself has to change with the width — the entity
 * list that is a table on the desk and rows on the phone, the sidebar footer
 * that becomes a menu on the rail. Pure styling stays in CSS media queries;
 * this hook is for choosing WHAT to render, never how it looks.
 *
 * The breakpoints below match shell.css exactly. Change both or neither.
 */
export function useMediaQuery(query: string): boolean {
  const subscribe = useCallback(
    (onChange: () => void) => {
      const list = window.matchMedia(query);
      list.addEventListener("change", onChange);
      return () => list.removeEventListener("change", onChange);
    },
    [query],
  );
  const getSnapshot = useCallback(() => window.matchMedia(query).matches, [query]);
  return useSyncExternalStore(subscribe, getSnapshot, () => false);
}

/** ≤ 600: phone — bottom tab bar, stacked header actions, 15px body. */
export function useIsPhone(): boolean {
  return useMediaQuery("(max-width: 600px)");
}

/** ≤ 760: entity lists render as `.list-rows` instead of a table. */
export function useIsNarrow(): boolean {
  return useMediaQuery("(max-width: 760px)");
}

/** ≤ 900: no sidebar — app bar + drawer. */
export function useIsCompact(): boolean {
  return useMediaQuery("(max-width: 900px)");
}

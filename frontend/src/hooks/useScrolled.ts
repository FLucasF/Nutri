import { useEffect, useState } from "react";

/**
 * Whether the window has scrolled at all.
 *
 * The sticky bars — the app bar, and the page header of a focused route on
 * the phone — lift with a shadow once the content has moved under them. Used
 * as `data-scrolled={scrolled ? "true" : "false"}` on the bar.
 */
export function useScrolled(): boolean {
  const [scrolled, setScrolled] = useState(() => window.scrollY > 0);
  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 0);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);
  return scrolled;
}

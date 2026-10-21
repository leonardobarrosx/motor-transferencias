import { useEffect, useState, useSyncExternalStore } from "react";

/** localStorage pode não existir (aba anônima, bloqueio de cookies): a interface funciona sem ele. */
function read(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string | null) {
  try {
    if (value === null) localStorage.removeItem(key);
    else localStorage.setItem(key, value);
  } catch {
    /* sem persistência, segue a vida */
  }
}

export function useStoredState(key: string, fallback: string): [string, (value: string) => void] {
  const [value, setValue] = useState(() => read(key) ?? fallback);
  return [value, (next) => { write(key, next); setValue(next); }];
}

export type Scheme = "light" | "dark";

const darkQuery = () => window.matchMedia("(prefers-color-scheme: dark)");

/** Claro/escuro como no portfólio: segue o sistema até a pessoa escolher, e lembra da escolha. */
export function useScheme(): [Scheme, (scheme: Scheme) => void] {
  const [chosen, setChosen] = useState<Scheme | null>(() => {
    const saved = read("mt:scheme");
    return saved === "light" || saved === "dark" ? saved : null;
  });
  const systemDark = useSyncExternalStore(
    (notify) => { const q = darkQuery(); q.addEventListener("change", notify); return () => q.removeEventListener("change", notify); },
    () => darkQuery().matches,
  );
  const scheme: Scheme = chosen ?? (systemDark ? "dark" : "light");

  useEffect(() => {
    const root = document.documentElement;
    if (chosen) root.dataset.scheme = chosen;
    else delete root.dataset.scheme;
    document.querySelector('meta[name="theme-color"]')?.setAttribute("content", scheme === "dark" ? "#1e1512" : "#efe9df");
  }, [chosen, scheme]);

  return [scheme, (next) => { write("mt:scheme", next); setChosen(next); }];
}

export type Route = "conta" | "transferir";

function currentRoute(): Route {
  return window.location.hash === "#/transferir" ? "transferir" : "conta";
}

export function useRoute(): Route {
  return useSyncExternalStore(
    (notify) => { window.addEventListener("hashchange", notify); return () => window.removeEventListener("hashchange", notify); },
    currentRoute,
  );
}

export function navigate(route: Route) {
  window.location.hash = `#/${route}`;
  window.scrollTo({ top: 0 });
}

const CLOCK = new Intl.DateTimeFormat("pt-BR", { timeZone: "America/Sao_Paulo", hour: "2-digit", minute: "2-digit" });

/** Hora de Brasília, que é a hora que importa para o Pix. */
export function useBrasiliaClock(): string {
  const [now, setNow] = useState(() => CLOCK.format(new Date()));
  useEffect(() => {
    const id = setInterval(() => setNow(CLOCK.format(new Date())), 15_000);
    return () => clearInterval(id);
  }, []);
  return now;
}

import { createContext, useCallback, useContext, useEffect, useState, useSyncExternalStore, type ReactNode } from "react";
import { session, type Session } from "../api";

/* ---------- rotas por hash: #/inicio, #/pix?chave=..., #/extrato ---------- */

export type RouteName = "inicio" | "pix" | "extrato";
export interface Route { name: RouteName; params: URLSearchParams }

function parse(): Route {
  const [path = "", query = ""] = window.location.hash.replace(/^#\/?/, "").split("?");
  const name: RouteName = path === "pix" || path === "extrato" ? path : "inicio";
  return { name, params: new URLSearchParams(query) };
}

let cached = parse();
const subscribeHash = (notify: () => void) => {
  const onChange = () => { cached = parse(); notify(); };
  window.addEventListener("hashchange", onChange);
  return () => window.removeEventListener("hashchange", onChange);
};

export const useRoute = () => useSyncExternalStore(subscribeHash, () => cached);

export function navigate(path: string) {
  window.location.hash = `#/${path}`;
  window.scrollTo({ top: 0 });
}

/* ---------- sessão ---------- */

export function useSession(): Session | null {
  return useSyncExternalStore(session.subscribe, session.get);
}

/* ---------- preferências ---------- */

function stored(key: string): string | null {
  try { return localStorage.getItem(key); } catch { return null; }
}
function store(key: string, value: string) {
  try { localStorage.setItem(key, value); } catch { /* sem persistência */ }
}

export type Scheme = "light" | "dark";

export function useScheme(): [Scheme, () => void] {
  const [scheme, setScheme] = useState<Scheme>(() => (stored("mt:scheme") === "dark" ? "dark" : "light"));
  useEffect(() => {
    document.documentElement.dataset.scheme = scheme;
    document.querySelector('meta[name="theme-color"]')?.setAttribute("content", scheme === "dark" ? "#17110e" : "#f5f2ed");
  }, [scheme]);
  return [scheme, () => setScheme((s) => { const next = s === "dark" ? "light" : "dark"; store("mt:scheme", next); return next; })];
}

/* ---------- valores ocultos e avisos ---------- */

interface Ui {
  hidden: boolean;
  toggleHidden: () => void;
  toast: (message: string) => void;
}

const UiContext = createContext<Ui>({ hidden: false, toggleHidden: () => {}, toast: () => {} });

export function UiProvider({ children }: { children: ReactNode }) {
  const [hidden, setHidden] = useState(() => stored("mt:hidden") === "1");
  const [message, setMessage] = useState<string | null>(null);

  const toggleHidden = useCallback(() => setHidden((h) => { store("mt:hidden", h ? "0" : "1"); return !h; }), []);
  const toast = useCallback((text: string) => setMessage(text), []);

  useEffect(() => {
    if (!message) return;
    const id = setTimeout(() => setMessage(null), 2400);
    return () => clearTimeout(id);
  }, [message]);

  return (
    <UiContext.Provider value={{ hidden, toggleHidden, toast }}>
      {children}
      {message && <div className="toast" role="status">{message}</div>}
    </UiContext.Provider>
  );
}

export const useUi = () => useContext(UiContext);

export async function copy(text: string, toast: (m: string) => void, label = "Copiado") {
  try {
    await navigator.clipboard.writeText(text);
    toast(label);
  } catch {
    toast("Não foi possível copiar");
  }
}

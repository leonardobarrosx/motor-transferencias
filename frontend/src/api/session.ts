import type { Session } from "./types";

/**
 * Sessão no sessionStorage: some ao fechar a aba e não é compartilhada entre
 * abas. Em produção o ideal é cookie HttpOnly + SameSite emitido pelo backend
 * (fora do alcance de JavaScript); aqui o token é Bearer, de 30 minutos.
 */
const KEY = "mt:session";
type Listener = (session: Session | null) => void;

const listeners = new Set<Listener>();
let current: Session | null = read();

function read(): Session | null {
  try {
    const raw = sessionStorage.getItem(KEY);
    const parsed = raw ? (JSON.parse(raw) as Session) : null;
    return parsed && parsed.expiresAt > Date.now() ? parsed : null;
  } catch {
    return null;
  }
}

export const session = {
  get: () => current,
  token: () => (current && current.expiresAt > Date.now() ? current.token : null),
  set(next: Session | null) {
    current = next;
    try {
      if (next) sessionStorage.setItem(KEY, JSON.stringify(next));
      else sessionStorage.removeItem(KEY);
    } catch {
      /* sem storage, a sessão vive só na memória */
    }
    listeners.forEach((listener) => listener(next));
  },
  subscribe(listener: Listener) {
    listeners.add(listener);
    return () => { listeners.delete(listener); };
  },
};

/** Motivo do último logout, para a tela de login explicar ("sua sessão expirou"). */
export let logoutReason: "expired" | null = null;

export function expire() {
  logoutReason = "expired";
  session.set(null);
}

export function consumeLogoutReason() {
  const reason = logoutReason;
  logoutReason = null;
  return reason;
}

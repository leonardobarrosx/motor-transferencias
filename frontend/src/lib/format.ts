const TZ = "America/Sao_Paulo";

const DAY_KEY = new Intl.DateTimeFormat("en-CA", { timeZone: TZ, year: "numeric", month: "2-digit", day: "2-digit" });
const DAY_LABEL = new Intl.DateTimeFormat("pt-BR", { timeZone: TZ, weekday: "short", day: "2-digit", month: "short" });
const TIME = new Intl.DateTimeFormat("pt-BR", { timeZone: TZ, hour: "2-digit", minute: "2-digit" });
const FULL = new Intl.DateTimeFormat("pt-BR", { timeZone: TZ, dateStyle: "long", timeStyle: "medium" });
const TODAY = new Intl.DateTimeFormat("pt-BR", { timeZone: TZ, weekday: "long", day: "numeric", month: "long" });
const HOUR = new Intl.DateTimeFormat("pt-BR", { timeZone: TZ, hour: "numeric", hourCycle: "h23" });

/** Chave do dia no fuso de Brasília ("2026-10-25"), para agrupar o extrato. */
export const dayKey = (iso: string | Date) => DAY_KEY.format(new Date(iso));

export function dayLabel(iso: string): string {
  const key = dayKey(iso);
  if (key === dayKey(new Date())) return "Hoje";
  if (key === dayKey(new Date(Date.now() - 86_400_000))) return "Ontem";
  return DAY_LABEL.format(new Date(iso)).replace(".", "");
}

export const time = (iso: string) => TIME.format(new Date(iso));
export const fullDate = (iso: string) => FULL.format(new Date(iso));
export const todayLong = () => TODAY.format(new Date());

export function greeting(): string {
  const hour = Number(HOUR.format(new Date()));
  return hour < 12 ? "Bom dia" : hour < 18 ? "Boa tarde" : "Boa noite";
}

export function initials(name: string): string {
  const parts = name.trim().split(/\s+/);
  return ((parts[0]?.[0] ?? "") + (parts.length > 1 ? parts[parts.length - 1]![0] : "")).toUpperCase();
}

export const firstName = (name: string) => name.trim().split(/\s+/)[0] ?? name;

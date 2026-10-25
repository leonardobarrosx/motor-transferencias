/**
 * Janelas de limite do Pix no horário de Brasília (UTC-3 o ano todo desde 2019):
 * diurna de 06h às 20h, noturna de 20h às 06h do dia seguinte. Mesma regra do
 * backend (PixLimits.java), usada pelo motor de demonstração.
 */
export type Period = "DAYTIME" | "NIGHTTIME";

const HOUR = 3_600_000;
const BRASILIA_OFFSET = -3 * HOUR;

export function pixWindow(now: Date): { period: Period; start: Date; end: Date } {
  const local = new Date(now.getTime() + BRASILIA_OFFSET);          // relógio de Brasília lido como UTC
  const midnight = Date.UTC(local.getUTCFullYear(), local.getUTCMonth(), local.getUTCDate());
  const hour = local.getUTCHours();
  const toUtc = (localMs: number) => new Date(localMs - BRASILIA_OFFSET);

  if (hour >= 6 && hour < 20) {
    return { period: "DAYTIME", start: toUtc(midnight + 6 * HOUR), end: toUtc(midnight + 20 * HOUR) };
  }
  const nightStart = hour < 6 ? midnight - 4 * HOUR : midnight + 20 * HOUR;   // antes das 6h, a noite começou ontem
  return { period: "NIGHTTIME", start: toUtc(nightStart), end: toUtc(nightStart + 10 * HOUR) };
}

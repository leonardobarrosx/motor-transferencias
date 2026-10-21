/**
 * Dinheiro no front vive em centavos inteiros. A API manda e recebe string
 * decimal ("150.75"), como a API Pix do Banco Central; ponto flutuante nunca
 * entra na conta (0.1 + 0.2 !== 0.3).
 */
export type Money = string;

const MONEY = /^(-)?(\d+)(?:\.(\d{1,2}))?$/;

export function toCents(value: Money): number {
  const match = MONEY.exec(value.trim());
  if (!match) throw new Error(`Valor monetário inválido: "${value}"`);
  const [, sign, units = "0", fraction = ""] = match;
  const cents = Number(units) * 100 + Number(fraction.padEnd(2, "0"));
  return sign ? -cents : cents;
}

export function fromCents(cents: number): Money {
  if (!Number.isSafeInteger(cents)) throw new Error(`Centavos inválidos: ${cents}`);
  const sign = cents < 0 ? "-" : "";
  const abs = Math.abs(cents);
  return `${sign}${Math.trunc(abs / 100)}.${String(abs % 100).padStart(2, "0")}`;
}

const BRL = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const UNITS = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 0 });

/** "R$ 1.234,56" */
export function formatBRL(cents: number): string {
  return BRL.format(cents / 100);
}

/** Partes para a tipografia display: { units: "24.849", cents: "25" }. */
export function splitBRL(cents: number): { units: string; cents: string } {
  const abs = Math.abs(cents);
  return { units: UNITS.format(Math.trunc(abs / 100)), cents: String(abs % 100).padStart(2, "0") };
}

const MAX_DIGITS = 13; // até R$ 99.999.999.999,99

/** Máscara de caixa eletrônico: cada dígito digitado entra pela direita. "15075" → 15075 centavos. */
export function centsFromDigits(input: string): number {
  const digits = input.replace(/\D/g, "").replace(/^0+/, "").slice(0, MAX_DIGITS);
  return digits === "" ? 0 : Number(digits);
}

/** O que aparece no campo de valor: "150,75". */
export function formatAmountInput(cents: number): string {
  if (cents === 0) return "";
  const { units, cents: c } = splitBRL(cents);
  return `${units},${c}`;
}

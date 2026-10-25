import { useUi } from "../hooks/app";
import { formatBRL } from "../lib/money";

/** Valor em reais que obedece ao "ocultar valores", como nos apps de banco. */
export function Money({ cents, sign, reveal = false }: { cents: number; sign?: "+" | "−"; reveal?: boolean }) {
  const { hidden } = useUi();
  if (hidden && !reveal) return <span className="num" aria-label="valor oculto">R$ •••••</span>;
  return <span className="num">{sign ? `${sign} ` : ""}{formatBRL(Math.abs(cents))}</span>;
}

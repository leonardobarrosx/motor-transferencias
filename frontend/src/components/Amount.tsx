import { splitBRL } from "../lib/money";

/** Valor em tipografia display: "R$" pequeno em vermelho, reais grandes, centavos menores. */
export function Amount({ cents, className = "", sign }: { cents: number; className?: string; sign?: "+" | "−" }) {
  const { units, cents: c } = splitBRL(cents);
  return (
    <span className={`display num ${className}`}>
      <small>{sign ?? ""}R$</small>
      {units}
      <span className="cents">,{c}</span>
    </span>
  );
}

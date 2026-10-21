/** Rótulo de seção do portfólio: "01 / Saldo  ———  残高". */
export function Label({ n, text, jp, as: Tag = "h2" }: { n: string; text: string; jp?: string; as?: "h1" | "h2" }) {
  return (
    <div className="label">
      <Tag className="mono">{n} / {text}</Tag>
      {jp && <span className="label__jp jp" aria-hidden="true">{jp}</span>}
      <span className="label__rule" aria-hidden="true" />
    </div>
  );
}

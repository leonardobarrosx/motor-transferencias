const ITEMS = ["Pix", "ACID", "送金", "Idempotência", "Lock pessimista", "振込", "Partidas dobradas", "Zero deadlock", "残高"];

/** Faixa corrida vermelha do portfólio, com o vocabulário do motor. */
export function Marquee() {
  const items = [...ITEMS, ...ITEMS];
  return (
    <div className="marquee" aria-hidden="true">
      <div className="marquee__track">
        {items.map((item, i) => (
          <span key={i} className={`marquee__item${/[぀-鿿]/.test(item) ? " jp" : ""}`}>{item}</span>
        ))}
      </div>
    </div>
  );
}

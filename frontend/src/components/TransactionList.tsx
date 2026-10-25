import type { StatementLine } from "../api";
import { dayKey, dayLabel, time } from "../lib/format";
import { toCents } from "../lib/money";
import { Icon } from "./Icon";
import { Money } from "./Money";

/** Movimentações agrupadas por dia (fuso de Brasília), como no extrato de um banco. */
export function TransactionList({ lines, onOpen }: { lines: StatementLine[]; onOpen: (line: StatementLine) => void }) {
  const groups = new Map<string, StatementLine[]>();
  for (const line of lines) {
    const key = dayKey(line.createdAt);
    groups.set(key, [...(groups.get(key) ?? []), line]);
  }

  return (
    <div>
      {[...groups.values()].map((day) => (
        <section key={dayKey(day[0]!.createdAt)} className="tx-group" aria-label={dayLabel(day[0]!.createdAt)}>
          <div className="tx-day">
            <span className="eyebrow">{dayLabel(day[0]!.createdAt)}</span>
          </div>
          <ul>
            {day.map((line) => {
              const credit = line.type === "CREDIT";
              return (
                <li key={line.entryId}>
                  <button type="button" className={`tx ${credit ? "tx--credit" : "tx--debit"}`} onClick={() => onOpen(line)}>
                    <span className="tx__icon"><Icon name={credit ? "in" : "out"} size={18} /></span>
                    <span className="tx__who">
                      <b>{credit ? "Pix recebido" : "Pix enviado"} · {line.counterpartyName}</b>
                      <span>{line.description ?? "Sem descrição"}</span>
                    </span>
                    <span className="tx__amount">
                      <b><Money cents={toCents(line.amount)} sign={credit ? "+" : "−"} /></b>
                      <span>{time(line.createdAt)}</span>
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        </section>
      ))}
    </div>
  );
}

export function TransactionSkeleton({ rows = 4 }: { rows?: number }) {
  return (
    <div aria-busy="true" aria-label="Carregando movimentações">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="skeleton-row">
          <span className="skeleton" />
          <span className="skeleton-row__lines">
            <span className="skeleton skeleton--title" />
            <span className="skeleton skeleton--text" />
          </span>
          <span className="skeleton skeleton--amount" />
        </div>
      ))}
    </div>
  );
}

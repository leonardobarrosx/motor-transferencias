import { useCallback, useEffect, useState } from "react";
import { api, type EntryType, type StatementLine } from "../api";
import { Icon } from "../components/Icon";
import { TransactionList, TransactionSkeleton } from "../components/TransactionList";
import { useRoute } from "../hooks/app";
import { TransferDetails } from "./TransferDetails";

const PAGE_SIZE = 15;
const FILTERS: { id: "todas" | "entradas" | "saidas"; label: string; type?: EntryType }[] = [
  { id: "todas", label: "Tudo" },
  { id: "entradas", label: "Entradas", type: "CREDIT" },
  { id: "saidas", label: "Saídas", type: "DEBIT" },
];

export function Statement() {
  const route = useRoute();
  const filter = FILTERS.find((f) => f.id === route.params.get("tipo")) ?? FILTERS[0]!;
  const [lines, setLines] = useState<StatementLine[]>([]);
  const [state, setState] = useState({ next: 0, total: 0, loading: true, error: false });
  const [open, setOpen] = useState<string | null>(null);

  const load = useCallback(async (page: number) => {
    setState((s) => ({ ...s, loading: true, error: false }));
    try {
      const result = await api.statement(page, PAGE_SIZE, filter.type);
      setLines((current) => (page === 0 ? result.content : [...current, ...result.content]));
      setState({ next: page + 1, total: result.totalElements, loading: false, error: false });
    } catch {
      setState((s) => ({ ...s, loading: false, error: true }));
    }
  }, [filter.type]);

  useEffect(() => { setLines([]); void load(0); }, [load]);

  return (
    <>
      <div className="page-head">
        <div>
          <h2>Extrato</h2>
          <p>Toque em uma movimentação para ver o comprovante.</p>
        </div>
        <div className="segmented" role="group" aria-label="Filtrar movimentações">
          {FILTERS.map((f) => (
            <button key={f.id} type="button" aria-pressed={f.id === filter.id}
              onClick={() => { window.location.hash = f.id === "todas" ? "#/extrato" : `#/extrato?tipo=${f.id}`; }}>
              {f.label}
            </button>
          ))}
        </div>
      </div>

      <section className="card" aria-label={`Movimentações: ${filter.label}`}>
        <div className="card__list">
          {lines.length === 0 && state.loading && <TransactionSkeleton rows={6} />}
          {lines.length === 0 && !state.loading && !state.error && (
            <div className="empty"><Icon name="receipt" size={32} />Nenhuma movimentação neste filtro.</div>
          )}
          {lines.length > 0 && <TransactionList lines={lines} onOpen={(line) => setOpen(line.transferId)} />}
          {state.error && (
            <div className="card__body">
              <div className="alert alert--error" role="alert">
                <Icon name="alert" size={18} />O extrato não carregou.
                <button className="link" type="button" onClick={() => load(state.next)}>Tentar de novo</button>
              </div>
            </div>
          )}
        </div>
        {lines.length > 0 && lines.length < state.total && (
          <div className="list-foot">
            <button className="btn btn--secondary" type="button" onClick={() => load(state.next)} disabled={state.loading}>
              {state.loading ? "Carregando…" : `Carregar mais (${state.total - lines.length})`}
            </button>
          </div>
        )}
      </section>

      {open && <TransferDetails transferId={open} onClose={() => setOpen(null)} />}
    </>
  );
}

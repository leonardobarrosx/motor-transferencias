import { useEffect, useState, type FormEvent } from "react";
import { api, ApiError, NetworkError, type RefundReason, type Transfer, type TransferOutcome } from "../api";
import { Drawer } from "../components/Drawer";
import { Icon } from "../components/Icon";
import { Receipt } from "../components/Receipt";
import { useAccount } from "../hooks/app";
import { centsFromDigits, formatAmountInput, formatBRL, fromCents, toCents } from "../lib/money";

const REASONS: { id: RefundReason; label: string }[] = [
  { id: "CUSTOMER_REQUEST", label: "Recebi por engano / combinei devolver" },
  { id: "FRAUD", label: "Suspeita de golpe" },
  { id: "BANK_ERROR", label: "Erro operacional" },
];

type View =
  | { name: "receipt" }
  | { name: "refund"; key: string; cents: number; reason: RefundReason; sending: boolean; error: string | null; retryable: boolean }
  | { name: "refunded"; outcome: TransferOutcome; key: string };

/**
 * Comprovante aberto a partir do extrato (GET /transfers/{id}). Se a conta logada
 * RECEBEU este Pix, oferece a devolução: total ou parcial, até o que falta devolver.
 */
export function TransferDetails({ transferId, onClose }: { transferId: string; onClose: () => void }) {
  const { account, refresh } = useAccount();
  const [transfer, setTransfer] = useState<Transfer | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [view, setView] = useState<View>({ name: "receipt" });

  useEffect(() => {
    api.transfer(transferId).then(setTransfer).catch((e: Error) => setError(e.message));
  }, [transferId]);

  const refundable = transfer && account && transfer.kind === "PIX" && transfer.status === "COMPLETED"
    && transfer.payee.accountId === account.id
    ? toCents(transfer.amount) - toCents(transfer.refundedAmount ?? "0")
    : 0;

  async function sendRefund(event?: FormEvent) {
    event?.preventDefault();
    if (view.name !== "refund" || !transfer) return;
    setView({ ...view, sending: true, error: null });
    try {
      const outcome = await api.refund(transfer.id, view.key, { amount: fromCents(view.cents), reason: view.reason });
      setView({ name: "refunded", outcome, key: view.key });
      refresh();
    } catch (e) {
      const retryable = e instanceof NetworkError || (e instanceof ApiError && e.code === "ACCOUNT_BUSY");
      setView({ ...view, sending: false, retryable, error: e instanceof Error ? e.message : "Erro inesperado." });
    }
  }

  return (
    <Drawer title={view.name === "receipt" ? "Comprovante" : "Devolver Pix"} onClose={onClose}>
      {error && <div className="alert alert--error" role="alert">{error}</div>}
      {!transfer && !error && <div className="skeleton skeleton--receipt" />}

      {transfer && view.name === "receipt" && (
        <div className="drawer__stack">
          <Receipt transfer={transfer} />
          {refundable > 0 && (
            <button type="button" className="btn btn--secondary btn--block"
              onClick={() => setView({ name: "refund", key: crypto.randomUUID(), cents: refundable, reason: "CUSTOMER_REQUEST", sending: false, error: null, retryable: false })}>
              <Icon name="refresh" size={18} />Devolver este Pix
            </button>
          )}
        </div>
      )}

      {transfer && view.name === "refund" && (
        <form className="drawer__stack" onSubmit={sendRefund} noValidate>
          <div className="alert alert--info">
            <Icon name="alert" size={18} />
            <span>O dinheiro volta para <b>{transfer.payer.holderName}</b>. Dá para devolver até <b>{formatBRL(refundable)}</b>
              {transfer.refundedAmount && toCents(transfer.refundedAmount) > 0 ? ` (já foram devolvidos ${formatBRL(toCents(transfer.refundedAmount))})` : ""}.</span>
          </div>
          <div className="field">
            <label htmlFor="refund-amount">Valor da devolução</label>
            <div className="amount-input">
              <span aria-hidden="true">R$</span>
              <input id="refund-amount" inputMode="numeric" autoComplete="off"
                value={formatAmountInput(view.cents)} disabled={view.sending}
                onChange={(e) => setView({ ...view, cents: Math.min(refundable, centsFromDigits(e.target.value)) })} />
            </div>
          </div>
          <div className="field">
            <label htmlFor="refund-reason">Motivo</label>
            <select id="refund-reason" className="input" value={view.reason} disabled={view.sending}
              onChange={(e) => setView({ ...view, reason: e.target.value as RefundReason })}>
              {REASONS.map((r) => <option key={r.id} value={r.id}>{r.label}</option>)}
            </select>
          </div>
          {view.error && (
            <div className="alert alert--error" role="alert"><Icon name="alert" size={18} />
              <span>{view.error}{view.retryable && " Reenviar é seguro: a Idempotency-Key é a mesma."}</span></div>
          )}
          <div className="actions actions--split">
            <button type="button" className="btn btn--ghost" onClick={() => setView({ name: "receipt" })} disabled={view.sending}>Cancelar</button>
            <button type="submit" className="btn btn--primary" disabled={view.sending || view.cents <= 0}>
              {view.sending ? "Devolvendo…" : `Devolver ${formatBRL(view.cents)}`}
            </button>
          </div>
        </form>
      )}

      {view.name === "refunded" && (
        view.outcome.kind === "completed"
          ? <Receipt transfer={view.outcome.transfer} idempotencyKey={view.key} />
          : <Receipt outcome={view.outcome} idempotencyKey={view.key} />
      )}
    </Drawer>
  );
}

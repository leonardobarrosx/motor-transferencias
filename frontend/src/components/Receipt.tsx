import { useEffect, useState } from "react";
import { api, type RejectionReason, type Transfer, type TransferOutcome } from "../api";
import { copy, useUi } from "../hooks/app";
import { fullDate } from "../lib/format";
import { formatBRL, toCents } from "../lib/money";
import { Icon } from "./Icon";

type Props =
  | { transfer: Transfer; outcome?: undefined; idempotencyKey?: string; attempts?: number }
  | { outcome: Extract<TransferOutcome, { kind: "rejected" }>; transfer?: undefined; idempotencyKey?: string; attempts?: number };

const REASONS: Record<RejectionReason, string> = {
  INSUFFICIENT_FUNDS: "Saldo insuficiente para esta transferência.",
  SOURCE_ACCOUNT_BLOCKED: "A conta de origem está bloqueada.",
  TARGET_ACCOUNT_BLOCKED: "A conta de destino não pode receber transferências.",
  LIMIT_EXCEEDED: "Valor acima do limite Pix disponível neste período.",
  RECEIVER_REJECTED: "O banco de quem ia receber recusou o Pix. Nenhum valor saiu da sua conta.",
};

/**
 * Pix para outro banco nasce "em processamento": o comprovante consulta a API até
 * o SPI responder, como os apps de banco fazem.
 */
function useSettled(initial: Transfer | undefined): Transfer | undefined {
  const [current, setCurrent] = useState(initial);
  useEffect(() => setCurrent(initial), [initial]);
  useEffect(() => {
    if (current?.status !== "PENDING") return;
    const timer = setTimeout(() => {
      api.transfer(current.id).then(setCurrent).catch(() => setCurrent({ ...current }));
    }, 1500);
    return () => clearTimeout(timer);
  }, [current]);
  return current;
}

/** Comprovante no formato de banco. Mostra os identificadores que um suporte pediria. */
export function Receipt(props: Props) {
  const { toast } = useUi();
  const t = useSettled(props.transfer);
  const rejected = props.outcome !== undefined || t?.status === "REJECTED";
  const pending = t?.status === "PENDING";
  const endToEndId = t?.endToEndId ?? props.outcome!.endToEndId;
  const transferId = t?.id ?? props.outcome!.transferId;

  return (
    <article className={`receipt${rejected ? " receipt--rejected" : ""}${pending ? " receipt--pending" : ""}`}
      aria-label="Comprovante" aria-busy={pending}>
      <header className="receipt__head">
        <span className="brand brand--sm"><i aria-hidden="true" />Motor</span>
        <span className="receipt__status">
          <span><Icon name={rejected ? "close" : pending ? "refresh" : "check"} size={16} /></span>
          {rejected ? "Transferência não realizada"
            : pending ? "Pix em processamento"
              : t?.kind === "REFUND" ? "Devolução de Pix realizada" : "Pix realizado com sucesso"}
        </span>
        {t && <span className="receipt__amount num">{formatBRL(toCents(t.amount))}</span>}
        {t && <span className="mono receipt__date">{fullDate(t.createdAt)}</span>}
      </header>

      <div className="receipt__body">
        <dl className="summary">
          {rejected && (
            <div><dt>Motivo</dt><dd>{props.outcome?.detail ?? (t?.rejectionReason ? REASONS[t.rejectionReason] : "Transferência recusada.")}</dd></div>
          )}
          {pending && (
            <div><dt>Situação</dt><dd>Enviado ao banco de destino. O valor está reservado e sai da conta quando ele confirmar.</dd></div>
          )}
          {t && (
            <>
              <div><dt>Para</dt><dd>{t.payee.holderName}</dd></div>
              <div><dt>Chave Pix</dt><dd>{t.payee.pixKey}</dd></div>
              <div><dt>De</dt><dd>{t.payer.holderName}</dd></div>
              <div><dt>Instituição</dt><dd>{t.payee.institution ?? "Motor de Transferências"}</dd></div>
              {t.description && <div><dt>Descrição</dt><dd>{t.description}</dd></div>}
              {t.refundedAmount && toCents(t.refundedAmount) > 0 && (
                <div><dt>Já devolvido</dt><dd className="num">{formatBRL(toCents(t.refundedAmount))}</dd></div>
              )}
            </>
          )}
        </dl>
      </div>
      <div className="receipt__cut" aria-hidden="true" />
      <div className="receipt__body">
        <dl className="summary receipt__ids">
          <div>
            <dt>ID da transação (E2E)</dt>
            <dd>
              <button type="button" className="copy copy--inline" onClick={() => copy(endToEndId, toast, "ID copiado")}>
                {endToEndId} <Icon name="copy" size={14} />
              </button>
            </dd>
          </div>
          <div><dt>Protocolo</dt><dd>{transferId}</dd></div>
          {t?.originalTransferId && <div><dt>Pix original</dt><dd>{t.originalTransferId}</dd></div>}
          {props.idempotencyKey && <div><dt>Idempotency-Key</dt><dd>{props.idempotencyKey}</dd></div>}
        </dl>
      </div>
      {props.attempts !== undefined && props.attempts > 1 && (
        <div className="receipt__foot">
          <div className="alert alert--success">
            <Icon name="shield" />
            <span>
              <b>{props.attempts} envios, 1 transferência.</b> O servidor reconheceu a Idempotency-Key e devolveu este
              comprovante original (<span className="mono">Idempotent-Replayed: true</span>). Nada foi debitado em dobro.
            </span>
          </div>
        </div>
      )}
    </article>
  );
}

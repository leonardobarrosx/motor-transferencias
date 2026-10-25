import type { Transfer, TransferOutcome } from "../api";
import { copy, useUi } from "../hooks/app";
import { fullDate } from "../lib/format";
import { formatBRL, toCents } from "../lib/money";
import { Icon } from "./Icon";

type Props =
  | { transfer: Transfer; outcome?: undefined; idempotencyKey?: string; attempts?: number }
  | { outcome: Extract<TransferOutcome, { kind: "rejected" }>; transfer?: undefined; idempotencyKey?: string; attempts?: number };

/** Comprovante no formato de banco. Mostra os identificadores que um suporte pediria. */
export function Receipt(props: Props) {
  const { toast } = useUi();
  const rejected = props.outcome !== undefined || props.transfer?.status === "REJECTED";
  const t = props.transfer;
  const endToEndId = t?.endToEndId ?? props.outcome!.endToEndId;
  const transferId = t?.id ?? props.outcome!.transferId;

  return (
    <article className={`receipt${rejected ? " receipt--rejected" : ""}`} aria-label="Comprovante">
      <header className="receipt__head">
        <span className="brand brand--sm"><i aria-hidden="true" />Motor</span>
        <span className="receipt__status">
          <span><Icon name={rejected ? "close" : "check"} size={16} /></span>
          {rejected ? "Transferência não realizada" : t?.kind === "REFUND" ? "Devolução de Pix realizada" : "Pix realizado com sucesso"}
        </span>
        {t && <span className="receipt__amount num">{formatBRL(toCents(t.amount))}</span>}
        {t && <span className="mono receipt__date">{fullDate(t.createdAt)}</span>}
      </header>

      <div className="receipt__body">
        <dl className="summary">
          {rejected && (
            <div><dt>Motivo</dt><dd>{props.outcome?.detail ?? "Transferência recusada."}</dd></div>
          )}
          {t && (
            <>
              <div><dt>Para</dt><dd>{t.payee.holderName}</dd></div>
              <div><dt>Chave Pix</dt><dd>{t.payee.pixKey}</dd></div>
              <div><dt>De</dt><dd>{t.payer.holderName}</dd></div>
              <div><dt>Instituição</dt><dd>Motor de Transferências</dd></div>
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

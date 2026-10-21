import { useEffect, useRef, useState, type FormEvent } from "react";
import { api, ApiError, NetworkError, type Account, type TransferOutcome } from "../api";
import { Amount } from "../components/Amount";
import { Label } from "../components/Label";
import { navigate } from "../hooks/preferences";
import { centsFromDigits, formatAmountInput, formatBRL, fromCents, toCents } from "../lib/money";

/**
 * Fluxo de envio: editar → revisar → enviar → comprovante.
 * A Idempotency-Key nasce na revisão e acompanha o pedido até o comprovante:
 * qualquer reenvio (rede caiu, conta ocupada) usa a mesma chave, então o motor
 * nunca debita duas vezes. Mudar o pedido volta para a edição e gera outra chave.
 */
type Step =
  | { name: "edit" }
  | { name: "review"; key: string }
  | { name: "sending"; key: string }
  | { name: "failed"; key: string; message: string; retryable: boolean }
  | { name: "done"; key: string; outcome: TransferOutcome; attempts: number };

interface Form {
  pixKey: string;
  cents: number;
  description: string;
}

const EMPTY: Form = { pixKey: "", cents: 0, description: "" };

const WHEN = new Intl.DateTimeFormat("pt-BR", {
  timeZone: "America/Sao_Paulo", dateStyle: "short", timeStyle: "medium",
});

export function Transfer({ accountId, accounts }: { accountId: string; accounts: Account[] }) {
  const [payer, setPayer] = useState<Account | null>(null);
  const [form, setForm] = useState<Form>(EMPTY);
  const [errors, setErrors] = useState<Partial<Record<keyof Form, string>>>({});
  const [step, setStep] = useState<Step>({ name: "edit" });
  const [loseNextResponse, setLoseNextResponse] = useState(false);
  const attempts = useRef(0);

  // relê o saldo ao trocar de conta e depois de cada envio concluído
  const done = step.name === "done";
  useEffect(() => {
    let alive = true;
    api.getAccount(accountId).then((a) => alive && setPayer(a)).catch(() => alive && setPayer(null));
    return () => { alive = false; };
  }, [accountId, done]);

  // trocar de conta no topo recomeça o pedido
  useEffect(() => { setForm(EMPTY); setStep({ name: "edit" }); }, [accountId]);

  const balance = payer ? toCents(payer.balance) : 0;
  const contacts = accounts.filter((a) => a.id !== accountId);
  const after = balance - form.cents;

  function review(event: FormEvent) {
    event.preventDefault();
    const found: typeof errors = {};
    if (!form.pixKey.trim()) found.pixKey = "Informe a chave Pix de destino.";
    if (form.cents <= 0) found.cents = "Informe um valor maior que zero.";
    setErrors(found);
    if (Object.keys(found).length === 0) {
      attempts.current = 0;
      setStep({ name: "review", key: crypto.randomUUID() });
    }
  }

  async function send(key: string) {
    setStep({ name: "sending", key });
    attempts.current += 1;
    try {
      const outcome = await api.createTransfer(key, {
        sourceAccountId: accountId,
        targetPixKey: form.pixKey.trim(),
        amount: fromCents(form.cents),
        description: form.description.trim() || undefined,
      });
      if (loseNextResponse) {
        // o servidor processou, mas a resposta "se perdeu": só a chave salva o reenvio
        setLoseNextResponse(false);
        throw new NetworkError("A resposta se perdeu no caminho (falha simulada). O Pix pode ou não ter saído.");
      }
      setStep({ name: "done", key, outcome, attempts: attempts.current });
    } catch (error) {
      if (error instanceof NetworkError) {
        setStep({ name: "failed", key, message: error.message, retryable: true });
      } else if (error instanceof ApiError) {
        setStep({ name: "failed", key, message: error.message, retryable: error.code === "ACCOUNT_BUSY" });
      } else {
        setStep({ name: "failed", key, message: "Erro inesperado.", retryable: false });
      }
    }
  }

  function restart() {
    setForm(EMPTY);
    setErrors({});
    setStep({ name: "edit" });
  }

  if (step.name === "done") {
    return <Receipt step={step} onAgain={restart} />;
  }

  const sheetOpen = step.name !== "edit";

  return (
    <>
      <Label n="02" text="Nova transferência" jp="振込" as="h1" />
      <div className="grid">
        <p className="transfer__title display" aria-hidden="true">
          <span>Pix na hora,</span>
          <span>uma vez só.</span>
        </p>

        <form className="transfer__form" onSubmit={review} noValidate>
          <div className="field">
            <div className="field__label">
              <label htmlFor="pix" className="mono">Para · chave Pix</label>
              <span className="mono muted">e-mail, telefone, CPF ou aleatória</span>
            </div>
            <input
              id="pix"
              className="field__input"
              value={form.pixKey}
              onChange={(e) => setForm({ ...form, pixKey: e.target.value })}
              placeholder="ana@motor.dev"
              autoComplete="off"
              spellCheck={false}
              maxLength={77}
              aria-invalid={Boolean(errors.pixKey)}
              aria-describedby={errors.pixKey ? "pix-error" : "pix-contacts"}
            />
            {errors.pixKey && <span id="pix-error" className="field__error">{errors.pixKey}</span>}
            {contacts.length > 0 && (
              <div className="chips" id="pix-contacts" role="group" aria-label="Contatos">
                {contacts.map((c) => (
                  <button key={c.id} type="button" aria-pressed={form.pixKey === c.pixKey}
                    onClick={() => setForm({ ...form, pixKey: c.pixKey })}>
                    {c.holderName}
                  </button>
                ))}
              </div>
            )}
          </div>

          <div className="field">
            <div className="field__label">
              <label htmlFor="amount" className="mono">Valor</label>
              {payer && <span className="mono muted">Disponível {formatBRL(balance)}</span>}
            </div>
            <div className="amount">
              <small aria-hidden="true">R$</small>
              <input
                id="amount"
                inputMode="numeric"
                value={formatAmountInput(form.cents)}
                onChange={(e) => setForm({ ...form, cents: centsFromDigits(e.target.value) })}
                placeholder="0,00"
                autoComplete="off"
                aria-invalid={Boolean(errors.cents)}
                aria-describedby="amount-hint"
              />
            </div>
            {errors.cents
              ? <span className="field__error">{errors.cents}</span>
              : form.cents > balance && payer
                ? <span id="amount-hint" className="field__hint">Acima do saldo: o motor vai recusar, e a recusa fica registrada.</span>
                : <span id="amount-hint" className="field__hint">Digite só os números; os centavos entram pela direita.</span>}
          </div>

          <div className="field">
            <div className="field__label">
              <label htmlFor="description" className="mono">Descrição · opcional</label>
              <span className="mono muted">{form.description.length}/140</span>
            </div>
            <input
              id="description"
              className="field__input"
              value={form.description}
              onChange={(e) => setForm({ ...form, description: e.target.value })}
              placeholder="Aluguel de outubro"
              maxLength={140}
            />
          </div>

          <div>
            <button className="btn btn--solid" type="submit" disabled={!payer || payer.status !== "ACTIVE"}>
              Revisar <span className="btn__arrow" aria-hidden="true">↗</span>
            </button>
          </div>
        </form>

        <aside className="transfer__aside" aria-label="Resumo">
          <dl className="panel">
            <div className="panel__row">
              <dt className="mono">De</dt>
              <dd>{payer?.holderName ?? "…"}</dd>
            </div>
            <div className="panel__row">
              <dt className="mono">Valor</dt>
              <dd className="panel__big"><Amount cents={form.cents} /></dd>
            </div>
            <div className="panel__row">
              <dt className="mono">Saldo depois</dt>
              <dd className="mono">{form.cents === 0 ? "—" : after >= 0 ? formatBRL(after) : "Insuficiente"}</dd>
            </div>
            <div className="panel__row">
              <dt className="mono">Idempotency-Key</dt>
              <dd className="mono">gerada na revisão</dd>
            </div>
            <label className="toggle">
              <input type="checkbox" checked={loseNextResponse} onChange={(e) => setLoseNextResponse(e.target.checked)} />
              <span>
                <b>Simular queda de rede.</b> O próximo envio chega ao motor, mas a resposta se perde. Reenvie e veja a
                idempotência devolver o mesmo comprovante, sem débito em dobro.
              </span>
            </label>
          </dl>
        </aside>
      </div>

      {sheetOpen && (
        <ConfirmSheet
          step={step}
          form={form}
          payee={contacts.find((c) => c.pixKey === form.pixKey.trim())}
          onSend={send}
          onClose={() => setStep({ name: "edit" })}
        />
      )}
    </>
  );
}

function ConfirmSheet({ step, form, payee, onSend, onClose }: {
  step: Exclude<Step, { name: "edit" } | { name: "done" }>;
  form: Form;
  payee?: Account;
  onSend: (key: string) => void;
  onClose: () => void;
}) {
  const title = useRef<HTMLHeadingElement>(null);
  const sending = step.name === "sending";

  useEffect(() => { title.current?.focus(); }, []);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape" && !sending) onClose(); };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose, sending]);

  return (
    <>
      <div className="sheet-backdrop" onClick={sending ? undefined : onClose} aria-hidden="true" />
      <div className="sheet" role="dialog" aria-modal="true" aria-labelledby="confirm-title">
        <div className="sheet__top">
          <span className="mono">Confirmar envio <span className="jp" aria-hidden="true">確認</span></span>
          <button className="sheet__close mono" type="button" onClick={onClose} disabled={sending}>Fechar ✕</button>
        </div>

        <div className="sheet__body">
          <h2 id="confirm-title" className="sheet__title display" tabIndex={-1} ref={title}>
            <Amount cents={form.cents} />
          </h2>
          <dl className="panel panel--flat">
            <div className="panel__row"><dt className="mono">Para</dt><dd>{payee?.holderName ?? "Destinatário da chave"}</dd></div>
            <div className="panel__row"><dt className="mono">Chave Pix</dt><dd>{form.pixKey.trim()}</dd></div>
            {form.description.trim() && (
              <div className="panel__row"><dt className="mono">Descrição</dt><dd>{form.description.trim()}</dd></div>
            )}
            <div className="panel__row"><dt className="mono">Idempotency-Key</dt><dd><code>{step.key}</code></dd></div>
          </dl>

          {step.name === "failed" && (
            <div className="banner banner--error" role="alert">
              <i aria-hidden="true" />
              <span>{step.message}{step.retryable && " Reenviar com a mesma chave é seguro."}</span>
            </div>
          )}
          <p className="mono" aria-live="polite">{sending ? "Enviando ao motor…" : ""}</p>
        </div>

        <div className="sheet__foot">
          {step.name === "failed" && step.retryable ? (
            <button className="btn btn--solid" type="button" onClick={() => onSend(step.key)}>
              Reenviar <span className="jp" aria-hidden="true">再送</span>
            </button>
          ) : (
            <button className="btn btn--solid" type="button" onClick={() => onSend(step.key)} disabled={sending || step.name === "failed"}>
              {sending ? "Enviando…" : "Enviar Pix"} <span className="btn__arrow" aria-hidden="true">↗</span>
            </button>
          )}
          <button className="link mono" type="button" onClick={onClose} disabled={sending}>Voltar e editar</button>
        </div>
      </div>
    </>
  );
}

function Receipt({ step, onAgain }: { step: Extract<Step, { name: "done" }>; onAgain: () => void }) {
  const { outcome } = step;
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    window.scrollTo({ top: 0 });
    heading.current?.focus({ preventScroll: true });
  }, []);

  const completed = outcome.kind === "completed";
  return (
    <>
      <Label n="02" text={completed ? "Comprovante" : "Transferência recusada"} jp={completed ? "完了" : "拒否"} as="h1" />
      <div className="grid">
        <div className="receipt" aria-live="polite">
          <article className={`poster ${completed ? "poster--red" : "poster--coffee"}`}>
            <span className="poster__deco" aria-hidden="true" />
            <div className="poster__meta mono">
              <span>{completed ? "Pix enviado" : outcome.reason}</span>
              <span>{step.attempts > 1 ? `${step.attempts} envios · 1 transferência` : "1 envio"}</span>
            </div>
            <h2 className="poster__title display" tabIndex={-1} ref={heading}>
              {completed ? <Amount cents={toCents(outcome.transfer.amount)} /> : "Recusado"}
            </h2>
            <div className="poster__foot">
              {completed ? (
                <dl>
                  <div><dt className="mono">Para</dt><dd>{outcome.transfer.payee.holderName}</dd></div>
                  <div><dt className="mono">Chave</dt><dd>{outcome.transfer.payee.pixKey}</dd></div>
                  <div><dt className="mono">Quando</dt><dd>{WHEN.format(new Date(outcome.transfer.createdAt))}</dd></div>
                  <div><dt className="mono">End-to-end</dt><dd className="mono">{outcome.transfer.endToEndId}</dd></div>
                  <div><dt className="mono">Idempotency-Key</dt><dd className="mono">{step.key}</dd></div>
                </dl>
              ) : (
                <dl>
                  <div><dt className="mono">Motivo</dt><dd>{outcome.detail}</dd></div>
                  <div><dt className="mono">End-to-end</dt><dd className="mono">{outcome.endToEndId}</dd></div>
                  <div><dt className="mono">Idempotency-Key</dt><dd className="mono">{step.key}</dd></div>
                </dl>
              )}
            </div>
          </article>

          {outcome.replayed && (
            <div className="banner receipt__note">
              <i aria-hidden="true" />
              <span>
                <b>Repetição reconhecida.</b> O motor já tinha processado esta Idempotency-Key e devolveu o resultado
                original (<span className="mono">Idempotent-Replayed: true</span>). Nenhum centavo saiu duas vezes.
              </span>
            </div>
          )}

          <div className="receipt__actions">
            <button className="btn" type="button" onClick={() => navigate("conta")}>Ver extrato</button>
            <button className="link mono" type="button" onClick={onAgain}>Nova transferência</button>
          </div>
        </div>
      </div>
    </>
  );
}

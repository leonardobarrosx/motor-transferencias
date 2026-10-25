import { useEffect, useRef, useState, type FormEvent, type RefObject } from "react";
import { api, ApiError, NetworkError, type Account, type Contact, type PixKey, type TransferOutcome } from "../api";
import { Icon } from "../components/Icon";
import { Money } from "../components/Money";
import { Receipt } from "../components/Receipt";
import { navigate, useRoute } from "../hooks/app";
import { initials } from "../lib/format";
import { centsFromDigits, formatAmountInput, formatBRL, fromCents, toCents } from "../lib/money";

/**
 * Pix em quatro etapas, como num app de banco: chave → valor → revisão → comprovante.
 *
 * A Idempotency-Key nasce quando a pessoa chega à revisão e acompanha o pedido
 * até o comprovante. Qualquer reenvio (rede caiu, conta ocupada) usa a mesma
 * chave, então o servidor nunca debita duas vezes. Voltar e editar gera outra.
 */
type Step = "key" | "amount" | "review" | "done";

type Sending =
  | { state: "idle" }
  | { state: "sending" }
  | { state: "failed"; message: string; retryable: boolean };

const STEPS: { id: Step; label: string }[] = [
  { id: "key", label: "Chave" },
  { id: "amount", label: "Valor" },
  { id: "review", label: "Revisão" },
  { id: "done", label: "Comprovante" },
];

export function Pix({ account, onCompleted }: { account: Account | null; onCompleted: () => void }) {
  const route = useRoute();
  const [step, setStep] = useState<Step>("key");
  const [pixKey, setPixKey] = useState(route.params.get("chave") ?? "");
  const [recipient, setRecipient] = useState<PixKey | null>(null);
  const [cents, setCents] = useState(0);
  const [description, setDescription] = useState("");
  const [idempotencyKey, setIdempotencyKey] = useState("");
  const [sending, setSending] = useState<Sending>({ state: "idle" });
  const [outcome, setOutcome] = useState<TransferOutcome | null>(null);
  const [loseNextResponse, setLoseNextResponse] = useState(false);
  const attempts = useRef(0);
  const heading = useRef<HTMLHeadingElement>(null);

  useEffect(() => { heading.current?.focus({ preventScroll: true }); }, [step]);

  function restart() {
    setStep("key"); setPixKey(""); setRecipient(null); setCents(0); setDescription("");
    setOutcome(null); setSending({ state: "idle" });
    window.location.hash = "#/pix";
  }

  function toReview() {
    attempts.current = 0;
    setIdempotencyKey(crypto.randomUUID());
    setSending({ state: "idle" });
    setStep("review");
  }

  async function send() {
    setSending({ state: "sending" });
    attempts.current += 1;
    try {
      const result = await api.createTransfer(idempotencyKey, {
        targetPixKey: recipient!.pixKey,
        amount: fromCents(cents),
        description: description.trim() || undefined,
      });
      if (loseNextResponse) {
        // o servidor processou, mas a resposta "se perdeu": só a chave salva o reenvio
        setLoseNextResponse(false);
        throw new NetworkError("A conexão caiu antes da resposta chegar (falha simulada). O Pix pode ou não ter saído.");
      }
      setOutcome(result);
      setStep("done");
      onCompleted();
    } catch (error) {
      if (error instanceof NetworkError) {
        setSending({ state: "failed", message: error.message, retryable: true });
      } else if (error instanceof ApiError) {
        setSending({ state: "failed", message: error.message, retryable: error.code === "ACCOUNT_BUSY" });
      } else {
        setSending({ state: "failed", message: "Erro inesperado.", retryable: false });
      }
    }
  }

  const index = STEPS.findIndex((s) => s.id === step);

  return (
    <div className="pix">
      <ol className="stepper" aria-label="Etapas do Pix">
        {STEPS.map((s, i) => (
          <li key={s.id} data-state={i < index ? "done" : i === index ? "current" : "todo"}
            aria-current={i === index ? "step" : undefined}>{s.label}</li>
        ))}
      </ol>

      {step === "key" && (
        <KeyStep heading={heading} pixKey={pixKey} setPixKey={setPixKey}
          onFound={(found) => { setRecipient(found); setStep("amount"); }} />
      )}

      {step === "amount" && recipient && (
        <section className="card">
          <form className="card__body" onSubmit={(e) => { e.preventDefault(); if (cents > 0) toReview(); }} noValidate>
            <h2 ref={heading} tabIndex={-1}>Quanto você quer transferir?</h2>
            <RecipientCard recipient={recipient} />
            {!recipient.acceptsTransfers && (
              <div className="alert alert--warning"><Icon name="alert" size={18} />
                Esta conta não está recebendo transferências. Você pode seguir, mas o banco vai recusar.</div>
            )}
            <div className="field">
              <label htmlFor="amount" className="sr-only">Valor</label>
              <div className="amount-input">
                <span aria-hidden="true">R$</span>
                <input id="amount" inputMode="numeric" autoComplete="off" placeholder="0,00" autoFocus
                  value={formatAmountInput(cents)} onChange={(e) => setCents(centsFromDigits(e.target.value))}
                  aria-describedby="amount-hint" />
              </div>
              <span id="amount-hint" className={account && cents > toCents(account.balance) ? "field__error" : "field__hint"}>
                {account && cents > toCents(account.balance)
                  ? "Valor acima do saldo disponível: o banco vai recusar e a recusa fica registrada."
                  : <>Saldo disponível: <Money cents={account ? toCents(account.balance) : 0} /></>}
              </span>
            </div>
            <div className="field">
              <label htmlFor="description">Mensagem (opcional)</label>
              <input id="description" className="input" maxLength={140} value={description}
                onChange={(e) => setDescription(e.target.value)} placeholder="Ex.: aluguel de outubro" />
            </div>
            <div className="actions actions--split">
              <button type="button" className="btn btn--ghost" onClick={() => setStep("key")}><Icon name="back" size={18} />Voltar</button>
              <button type="submit" className="btn btn--primary" disabled={cents <= 0}>Continuar</button>
            </div>
          </form>
        </section>
      )}

      {step === "review" && recipient && (
        <section className="card">
          <div className="card__body">
            <h2 ref={heading} tabIndex={-1}>Confira antes de enviar</h2>
            <dl className="summary">
              <div className="summary__total"><dt>Valor</dt><dd className="num">{formatBRL(cents)}</dd></div>
              <div><dt>Para</dt><dd>{recipient.holderName}</dd></div>
              <div><dt>CPF/CNPJ</dt><dd>{recipient.document}</dd></div>
              <div><dt>Chave Pix</dt><dd>{recipient.pixKey}</dd></div>
              <div><dt>Instituição</dt><dd>{recipient.institution}</dd></div>
              {description.trim() && <div><dt>Mensagem</dt><dd>{description.trim()}</dd></div>}
              <div><dt>Data</dt><dd>Agora · Pix instantâneo</dd></div>
            </dl>

            <details className="tech">
              <summary><Icon name="bolt" size={16} />Detalhes técnicos (para a demonstração)</summary>
              <div className="tech__body">
                <p>Este pedido vai com o header <code>Idempotency-Key: {idempotencyKey}</code>. Se a resposta se perder e
                  você reenviar, o servidor reconhece a chave e devolve o mesmo comprovante.</p>
                <label className="toggle">
                  <input type="checkbox" checked={loseNextResponse} onChange={(e) => setLoseNextResponse(e.target.checked)} />
                  <span><b>Simular queda de rede</b>: o próximo envio chega ao servidor, mas a resposta é descartada.</span>
                </label>
              </div>
            </details>

            {sending.state === "failed" && (
              <div className="alert alert--error" role="alert">
                <Icon name="alert" size={18} />
                <span>{sending.message}{sending.retryable && <> <b>Reenviar é seguro</b>: a mesma Idempotency-Key impede débito em dobro.</>}</span>
              </div>
            )}

            <div className="actions actions--split">
              <button type="button" className="btn btn--ghost" onClick={() => setStep("amount")} disabled={sending.state === "sending"}>
                <Icon name="back" size={18} />Editar
              </button>
              {sending.state === "failed" && sending.retryable ? (
                <button type="button" className="btn btn--primary btn--lg" onClick={send}><Icon name="refresh" size={18} />Reenviar com segurança</button>
              ) : (
                <button type="button" className="btn btn--primary btn--lg" onClick={send}
                  disabled={sending.state === "sending" || sending.state === "failed"}>
                  {sending.state === "sending" ? "Enviando…" : <>Confirmar Pix de {formatBRL(cents)}</>}
                </button>
              )}
            </div>
          </div>
        </section>
      )}

      {step === "done" && outcome && (
        <>
          <h2 ref={heading} tabIndex={-1} className="sr-only">
            {outcome.kind === "completed" ? "Pix enviado" : "Transferência não realizada"}
          </h2>
          {outcome.kind === "completed"
            ? <Receipt transfer={outcome.transfer} idempotencyKey={idempotencyKey} attempts={attempts.current} />
            : <Receipt outcome={outcome} idempotencyKey={idempotencyKey} attempts={attempts.current} />}
          <div className="actions">
            <button type="button" className="btn btn--secondary" onClick={() => navigate("inicio")}>Ir para o início</button>
            <button type="button" className="btn btn--primary" onClick={restart}>Fazer outro Pix</button>
          </div>
        </>
      )}
    </div>
  );
}

function KeyStep({ heading, pixKey, setPixKey, onFound }: {
  heading: RefObject<HTMLHeadingElement | null>;
  pixKey: string;
  setPixKey: (key: string) => void;
  onFound: (key: PixKey) => void;
}) {
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const autoLookup = useRef(Boolean(pixKey));

  useEffect(() => { api.contacts().then(setContacts).catch(() => setContacts([])); }, []);

  async function lookup(key: string) {
    if (!key.trim()) { setError("Informe a chave Pix de quem vai receber."); return; }
    setError(null);
    setLoading(true);
    try {
      onFound(await api.lookupPixKey(key));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Não foi possível consultar a chave.");
      setLoading(false);
    }
  }

  // chegou com ?chave= (atalho de contato): consulta uma vez, direto
  useEffect(() => {
    if (autoLookup.current) { autoLookup.current = false; void lookup(pixKey); }
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    void lookup(pixKey);
  }

  return (
    <section className="card">
      <form className="card__body" onSubmit={submit} noValidate>
        <h2 ref={heading} tabIndex={-1}>Para quem você quer enviar?</h2>
        <div className="field">
          <label htmlFor="pix-key">Chave Pix</label>
          <input id="pix-key" className="input" autoComplete="off" spellCheck={false} maxLength={77} autoFocus
            placeholder="CPF, CNPJ, e-mail, telefone ou chave aleatória"
            value={pixKey} onChange={(e) => { setPixKey(e.target.value); setError(null); }}
            aria-invalid={Boolean(error)} aria-describedby={error ? "pix-key-error" : undefined} />
          {error && <span id="pix-key-error" className="field__error" role="alert">{error}</span>}
        </div>
        {contacts.length > 0 && (
          <div className="field">
            <span className="field__label">Contatos recentes</span>
            <div className="chips">
              {contacts.map((c) => (
                <button key={c.pixKey} type="button" className="chip" onClick={() => { setPixKey(c.pixKey); void lookup(c.pixKey); }}>
                  <span className="avatar avatar--soft avatar--xs" aria-hidden="true">{initials(c.holderName)}</span>
                  {c.holderName}
                </button>
              ))}
            </div>
          </div>
        )}
        <div className="actions">
          <button type="submit" className="btn btn--primary" disabled={loading}>{loading ? "Consultando…" : "Continuar"}</button>
        </div>
      </form>
    </section>
  );
}

function RecipientCard({ recipient }: { recipient: PixKey }) {
  return (
    <div className="recipient">
      <span className="avatar" aria-hidden="true">{initials(recipient.holderName)}</span>
      <dl>
        <dt>{recipient.holderName}</dt>
        <dd>{recipient.document} · {recipient.institution}</dd>
        <dd className="mono">Ag {recipient.branch} · Cc {recipient.accountNumber} · {recipient.pixKey}</dd>
      </dl>
    </div>
  );
}

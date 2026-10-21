import { useCallback, useEffect, useState } from "react";
import { api, type Account, type StatementLine } from "../api";
import { Amount } from "../components/Amount";
import { Label } from "../components/Label";
import { Marquee } from "../components/Marquee";
import { navigate } from "../hooks/preferences";
import { formatBRL, toCents } from "../lib/money";

const PAGE_SIZE = 8;

const WHEN = new Intl.DateTimeFormat("pt-BR", {
  timeZone: "America/Sao_Paulo", day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit",
});
const MONTH = new Intl.DateTimeFormat("pt-BR", { timeZone: "America/Sao_Paulo", month: "long" });

type Load<T> = { state: "loading" } | { state: "ready"; data: T } | { state: "error"; message: string };

export function Dashboard({ accountId }: { accountId: string }) {
  const [account, setAccount] = useState<Load<Account>>({ state: "loading" });
  const [lines, setLines] = useState<StatementLine[]>([]);
  const [page, setPage] = useState({ next: 0, total: 0, loading: true, error: false });

  const loadStatement = useCallback(async (pageNumber: number) => {
    setPage((p) => ({ ...p, loading: true, error: false }));
    try {
      const result = await api.getStatement(accountId, pageNumber, PAGE_SIZE);
      setLines((current) => (pageNumber === 0 ? result.content : [...current, ...result.content]));
      setPage({ next: pageNumber + 1, total: result.totalElements, loading: false, error: false });
    } catch {
      setPage((p) => ({ ...p, loading: false, error: true }));
    }
  }, [accountId]);

  useEffect(() => {
    let alive = true;
    setAccount({ state: "loading" });
    setLines([]);
    api.getAccount(accountId)
      .then((data) => alive && setAccount({ state: "ready", data }))
      .catch((e: Error) => alive && setAccount({ state: "error", message: e.message }));
    void loadStatement(0);
    return () => { alive = false; };
  }, [accountId, loadStatement]);

  if (account.state === "loading") {
    return <div className="state" aria-busy="true"><span className="mono">Carregando conta…</span></div>;
  }
  if (account.state === "error") {
    return (
      <div className="state" role="alert">
        <span className="mono">Não foi possível abrir a conta</span>
        <p>{account.message}</p>
        <button className="btn" type="button" onClick={() => location.reload()}>Tentar de novo</button>
      </div>
    );
  }

  const a = account.data;
  const month = a.monthToDate;
  const blocked = a.status === "BLOCKED";

  return (
    <>
      <section aria-labelledby="saldo">
        <div className="meta mono">
          <span>Ag {a.branch} · Cc {a.number}</span>
          <span>Pix {a.pixKey}</span>
          <span>{a.document}</span>
        </div>

        <div className="balance">
          <span className="balance__sun" aria-hidden="true" />
          <Label n="01" text="Saldo disponível" jp="残高" as="h1" />
          <p id="saldo" className="balance__value">
            <span className="sr-only">Saldo de </span>
            <Amount cents={toCents(a.balance)} />
          </p>
          <p className="balance__statement">
            Olá, {a.holderName.split(" ")[0]}. Cada centavo daqui foi registrado <em className="serif-i">duas vezes</em>:
            um débito de um lado, um crédito do outro.
          </p>
          <div className="balance__actions">
            <button className="btn btn--solid" type="button" onClick={() => navigate("transferir")} disabled={blocked}>
              Transferir <span className="btn__arrow" aria-hidden="true">↗</span>
            </button>
            {blocked && <span className="mono muted">Conta bloqueada para envios</span>}
          </div>
        </div>
      </section>

      <Marquee />

      {month && (
        <section className="section" aria-label="Movimento do mês">
          <Label n="02" text={`Movimento de ${MONTH.format(new Date())}`} jp="今月" />
          <div className="posters">
            <article className="poster poster--red">
              <span className="poster__deco" aria-hidden="true" />
              <div className="poster__meta mono"><span>Entradas</span><span>+</span></div>
              <p className="poster__title"><Amount cents={toCents(month.inflow)} /></p>
              <p className="poster__foot">Créditos recebidos via Pix desde o dia 1º.</p>
            </article>
            <article className="poster poster--coffee">
              <span className="poster__deco" aria-hidden="true" />
              <div className="poster__meta mono"><span>Saídas</span><span>−</span></div>
              <p className="poster__title"><Amount cents={toCents(month.outflow)} /></p>
              <p className="poster__foot">Débitos enviados, já descontados do saldo.</p>
            </article>
            <article className="poster poster--paper">
              <span className="poster__deco" aria-hidden="true" />
              <div className="poster__meta mono"><span>Lançamentos</span><span>Nº</span></div>
              <p className="poster__title display num">{String(month.transfers).padStart(2, "0")}</p>
              <p className="poster__foot">Recusas não entram: só o que movimentou dinheiro.</p>
            </article>
          </div>
        </section>
      )}

      <section className="section" aria-labelledby="extrato">
        <Label n="03" text="Extrato" jp="明細" />
        <h2 id="extrato" className="sr-only">Extrato</h2>

        {lines.length === 0 && page.loading && (
          <div aria-busy="true">{Array.from({ length: 4 }, (_, i) => <div key={i} className="skeleton" />)}</div>
        )}
        {lines.length === 0 && !page.loading && !page.error && (
          <div className="state"><span className="mono">Nenhum lançamento ainda</span><p>A primeira transferência aparece aqui.</p></div>
        )}

        {lines.length > 0 && (
          <ol className="rows">
            {lines.map((line) => {
              const credit = line.type === "CREDIT";
              return (
                <li key={line.entryId} className={`row ${credit ? "row--credit" : "row--debit"}`}>
                  <time className="row__when mono" dateTime={line.createdAt}>{WHEN.format(new Date(line.createdAt))}</time>
                  <div className="row__who">
                    <b>{credit ? "De " : "Para "}{line.counterpartyName}</b>
                    <span>{line.description ?? "Pix"} · <span className="mono">{line.endToEndId}</span></span>
                  </div>
                  <div className="row__amount">
                    <strong>
                      <i aria-hidden="true" />
                      <span className="sr-only">{credit ? "Crédito de" : "Débito de"}</span>
                      {credit ? "+" : "−"} {formatBRL(toCents(line.amount))}
                    </strong>
                    <span className="mono muted">Saldo {formatBRL(toCents(line.balanceAfter))}</span>
                  </div>
                </li>
              );
            })}
          </ol>
        )}

        {page.error && (
          <div className="banner banner--error" role="alert"><i aria-hidden="true" />O extrato não carregou.
            <button className="link mono" type="button" onClick={() => loadStatement(page.next)}>Tentar de novo</button>
          </div>
        )}
        {lines.length > 0 && lines.length < page.total && (
          <div className="rows__more">
            <div className="pill">
              <button type="button" onClick={() => loadStatement(page.next)} disabled={page.loading}>
                {page.loading ? "Carregando…" : `Mais lançamentos (${page.total - lines.length})`}
              </button>
            </div>
          </div>
        )}
      </section>
    </>
  );
}

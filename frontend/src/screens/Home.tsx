import { useEffect, useState } from "react";
import { api, type Account, type Contact, type StatementLine } from "../api";
import { Icon } from "../components/Icon";
import { LimitCard } from "../components/LimitCard";
import { Money } from "../components/Money";
import { TransactionList, TransactionSkeleton } from "../components/TransactionList";
import { copy, navigate, useUi } from "../hooks/app";
import { initials } from "../lib/format";
import { toCents } from "../lib/money";
import { TransferDetails } from "./TransferDetails";

export function Home({ account }: { account: Account | null }) {
  const { hidden, toggleHidden, toast } = useUi();
  const [recent, setRecent] = useState<StatementLine[] | null>(null);
  const [contacts, setContacts] = useState<Contact[] | null>(null);
  const [open, setOpen] = useState<string | null>(null);

  useEffect(() => {
    api.statement(0, 6).then((page) => setRecent(page.content)).catch(() => setRecent([]));
    api.contacts().then(setContacts).catch(() => setContacts([]));
  }, []);

  const month = account?.monthToDate;
  const inflow = month ? toCents(month.inflow) : 0;
  const outflow = month ? toCents(month.outflow) : 0;
  const total = inflow + outflow || 1;
  const blocked = account?.status === "BLOCKED";

  return (
    <div className="home">
      <div className="home__main">
        <section className="balance-card" aria-labelledby="saldo-label">
          <span className="balance-card__disc" aria-hidden="true" />
          <div className="balance-card__top">
            <span className="balance-card__label" id="saldo-label">
              Saldo disponível
              <button type="button" className="icon-btn" onClick={toggleHidden} aria-label={hidden ? "Mostrar saldo" : "Ocultar saldo"}>
                <Icon name={hidden ? "eyeOff" : "eye"} size={18} />
              </button>
            </span>
            <span className="eyebrow balance-card__kind">Conta corrente</span>
          </div>
          <p className="balance-card__value">
            {account ? <Money cents={toCents(account.balance)} /> : <span className="skeleton skeleton--balance" />}
          </p>
          {account && (
            <div className="balance-card__meta mono">
              <span>Ag <b>{account.branch}</b></span>
              <span>Cc <b>{account.number}</b></span>
              <button type="button" className="copy mono" onClick={() => copy(account.pixKey, toast, "Chave Pix copiada")}>
                Pix <b>{account.pixKey}</b> <Icon name="copy" size={14} />
              </button>
            </div>
          )}
          {blocked && (
            <div className="alert alert--warning"><Icon name="lock" size={18} />Esta conta está bloqueada para envios. Os Pix serão recusados.</div>
          )}
          <div className="balance-card__actions">
            <a className="btn btn--primary" href="#/pix"><Icon name="pix" size={18} />Fazer um Pix</a>
            <a className="btn btn--on-dark" href="#/extrato">Ver extrato</a>
          </div>
        </section>

        <nav className="quick" aria-label="Atalhos">
          <a href="#/pix"><span className="quick__icon"><Icon name="pix" /></span>Transferir<span>Pix por chave</span></a>
          <a href="#/extrato"><span className="quick__icon"><Icon name="list" /></span>Extrato<span>Entradas e saídas</span></a>
          <button type="button" onClick={() => account && copy(account.pixKey, toast, "Chave Pix copiada")}>
            <span className="quick__icon"><Icon name="key" /></span>Minha chave<span>Copiar para receber</span>
          </button>
          <a href="#/extrato?tipo=saidas"><span className="quick__icon"><Icon name="receipt" /></span>Comprovantes<span>Pix enviados</span></a>
        </nav>

        <section className="card" aria-labelledby="mes">
          <div className="card__head"><h2 id="mes">Resumo do mês</h2><span className="eyebrow">{month?.transfers ?? 0} lançamentos</span></div>
          <div className="card__body month">
            <div className="stat">
              <span className="muted"><span className="stat__dot stat__dot--in" />Entradas</span>
              <strong className="positive"><Money cents={inflow} sign="+" /></strong>
            </div>
            <div className="stat">
              <span className="muted"><span className="stat__dot stat__dot--out" />Saídas</span>
              <strong><Money cents={outflow} sign="−" /></strong>
            </div>
            <div className="ratio" role="img" aria-label={`Entradas ${Math.round((inflow / total) * 100)}% do movimento do mês`}>
              <span style={{ width: `${(inflow / total) * 100}%` }} />
              <span style={{ width: `${(outflow / total) * 100}%` }} />
            </div>
          </div>
        </section>
      </div>

      <div className="home__side">
        <LimitCard />
        <section className="card" aria-labelledby="contatos">
          <div className="card__head"><h2 id="contatos">Contatos Pix recentes</h2></div>
          <div className="card__list">
            {contacts === null && <TransactionSkeleton rows={3} />}
            {contacts?.length === 0 && <p className="empty">Ninguém por aqui ainda.</p>}
            <ul className="contacts">
              {contacts?.map((c) => (
                <li key={c.pixKey}>
                  <a className="contact" href={`#/pix?chave=${encodeURIComponent(c.pixKey)}`}>
                    <span className="avatar avatar--soft" aria-hidden="true">{initials(c.holderName)}</span>
                    <div><b>{c.holderName}</b><span>{c.pixKey}</span></div>
                    <Icon name="chevron" size={18} />
                  </a>
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section className="card" aria-labelledby="ultimas">
          <div className="card__head"><h2 id="ultimas">Últimas movimentações</h2></div>
          <div className="card__list">
            {recent === null && <TransactionSkeleton />}
            {recent?.length === 0 && <div className="empty"><Icon name="receipt" size={32} />Nenhuma movimentação ainda.</div>}
            {recent && recent.length > 0 && <TransactionList lines={recent} onOpen={(line) => setOpen(line.transferId)} />}
          </div>
          <div className="list-foot"><button className="btn btn--ghost" type="button" onClick={() => navigate("extrato")}>Ver extrato completo</button></div>
        </section>
      </div>

      {open && <TransferDetails transferId={open} onClose={() => setOpen(null)} />}
    </div>
  );
}

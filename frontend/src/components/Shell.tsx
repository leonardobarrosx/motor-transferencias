import { useEffect, useState, type ReactNode } from "react";
import { api, session, type Account } from "../api";
import { useRoute, useScheme, useSession, useUi, type RouteName } from "../hooks/app";
import { firstName, greeting, initials, todayLong } from "../lib/format";
import { Icon, type IconName } from "./Icon";

const NAV: { route: RouteName; label: string; icon: IconName }[] = [
  { route: "inicio", label: "Início", icon: "home" },
  { route: "pix", label: "Pix", icon: "pix" },
  { route: "extrato", label: "Extrato", icon: "list" },
];

const TITLES: Record<RouteName, string> = { inicio: "Início", pix: "Área Pix", extrato: "Extrato" };

/** Minutos restantes do token; ao zerar, a sessão termina sozinha. */
function useSessionCountdown(expiresAt: number) {
  const [left, setLeft] = useState(() => expiresAt - Date.now());
  useEffect(() => {
    const id = setInterval(() => {
      const ms = expiresAt - Date.now();
      setLeft(ms);
      if (ms <= 0) session.set(null);
    }, 15_000);
    return () => clearInterval(id);
  }, [expiresAt]);
  return Math.max(0, Math.ceil(left / 60_000));
}

export function Shell({ account, children }: { account: Account | null; children: ReactNode }) {
  const route = useRoute();
  const current = useSession();
  const { hidden, toggleHidden } = useUi();
  const [scheme, toggleScheme] = useScheme();
  const minutes = useSessionCountdown(current?.expiresAt ?? 0);
  const name = account?.holderName ?? current?.holderName ?? "";

  const links = (className?: string) => NAV.map((item) => (
    <a key={item.route} href={`#/${item.route}`} className={className}
      aria-current={route.name === item.route ? "page" : undefined}>
      <Icon name={item.icon} size={20} />
      {item.label}
    </a>
  ));

  return (
    <div className="shell">
      <a href="#conteudo" className="skip">Pular para o conteúdo</a>

      <aside className="sidebar" aria-label="Menu">
        <span className="brand"><i aria-hidden="true" />Motor<small>banco</small></span>
        <nav className="nav" aria-label="Principal">{links()}</nav>
        <div className="sidebar__user">
          <div className="user-chip">
            <span className="avatar" aria-hidden="true">{initials(name)}</span>
            <div>
              <b>{name}</b>
              {account && <span>Ag {account.branch} · Cc {account.number}</span>}
            </div>
          </div>
          <div className="session">
            <span className="mono" title="Tempo até o token expirar">Sessão · {minutes} min</span>
            <button type="button" onClick={() => session.set(null)}><Icon name="logout" size={16} />Sair</button>
          </div>
        </div>
      </aside>

      <div className="main">
        {api.mode === "demo" && (
          <div className="demo-banner"><Icon name="alert" size={14} />Modo demonstração: dados fictícios, motor rodando no navegador.</div>
        )}
        <header className="topbar">
          <span className="brand topbar__brand"><i aria-hidden="true" />Motor</span>
          <div className="topbar__title">
            <h1>{route.name === "inicio" ? `${greeting()}, ${firstName(name)}` : TITLES[route.name]}</h1>
            <p>{todayLong()}</p>
          </div>
          <div className="topbar__actions">
            <button className="icon-btn" type="button" onClick={toggleHidden}
              aria-pressed={hidden} aria-label={hidden ? "Mostrar valores" : "Ocultar valores"} title={hidden ? "Mostrar valores" : "Ocultar valores"}>
              <Icon name={hidden ? "eyeOff" : "eye"} />
            </button>
            <button className="icon-btn" type="button" onClick={toggleScheme}
              aria-label={scheme === "dark" ? "Usar tema claro" : "Usar tema escuro"} title="Alternar tema">
              <Icon name={scheme === "dark" ? "sun" : "moon"} />
            </button>
            <button className="icon-btn" type="button" onClick={() => session.set(null)} aria-label="Sair" title="Sair">
              <Icon name="logout" />
            </button>
          </div>
        </header>
        <main id="conteudo" className="content" tabIndex={-1}>{children}</main>
      </div>

      <nav className="tabbar" aria-label="Principal">{links()}</nav>
    </div>
  );
}

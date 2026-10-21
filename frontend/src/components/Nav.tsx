import type { Account } from "../api";
import { useBrasiliaClock, type Route, type Scheme } from "../hooks/preferences";

interface Props {
  route: Route;
  accounts: Account[];
  accountId: string;
  onAccountChange: (id: string) => void;
  scheme: Scheme;
  onSchemeChange: (scheme: Scheme) => void;
}

export function Nav({ route, accounts, accountId, onAccountChange, scheme, onSchemeChange }: Props) {
  const clock = useBrasiliaClock();
  return (
    <header className="nav">
      <div className="wrap nav__bar">
        <a href="#/conta" className="nav__mark" aria-label="Motor de Transferências, início">
          <i aria-hidden="true" />Motor
        </a>

        <nav className="pill nav__links" aria-label="Principal">
          <a href="#/conta" aria-current={route === "conta" ? "page" : undefined}><small>01</small>Conta</a>
          <a href="#/transferir" aria-current={route === "transferir" ? "page" : undefined}><small>02</small>Transferir</a>
        </nav>

        <div className="nav__right">
          <span className="mono nav__clock" aria-label={`Horário de Brasília ${clock}`}>BRT {clock}</span>
          {accounts.length > 0 && (
            <label className="pill">
              <span className="sr-only">Conta em uso</span>
              <select value={accountId} onChange={(e) => onAccountChange(e.target.value)}>
                {accounts.map((a) => (
                  <option key={a.id} value={a.id}>{a.holderName}</option>
                ))}
              </select>
            </label>
          )}
          <div className="pill nav__scheme" role="group" aria-label="Esquema de cores">
            <button type="button" aria-pressed={scheme === "light"} onClick={() => onSchemeChange("light")}>Claro</button>
            <button type="button" aria-pressed={scheme === "dark"} onClick={() => onSchemeChange("dark")}>Escuro</button>
          </div>
        </div>
      </div>
    </header>
  );
}

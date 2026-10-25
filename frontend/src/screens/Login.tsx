import { useState, type FormEvent } from "react";
import { api, ApiError, consumeLogoutReason, NetworkError, session } from "../api";
import { Icon } from "../components/Icon";

const DEMO_USERS = [
  { email: "leonardo@motor.dev", name: "Leonardo Barros", note: "Conta principal" },
  { email: "ana@motor.dev", name: "Ana Souza", note: "Recebe e envia Pix" },
  { email: "carlos@motor.dev", name: "Carlos Lima", note: "Saldo baixo" },
  { email: "bloqueada@motor.dev", name: "Conta Bloqueada", note: "Para ver a recusa" },
];
const DEMO_PASSWORD = "motor123";

export function Login() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<string | null>(() =>
    consumeLogoutReason() === "expired" ? "Sua sessão expirou. Entre novamente." : null);
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      session.set(await api.login(email, password));
      window.location.hash = "#/inicio";
    } catch (e) {
      setError(e instanceof ApiError || e instanceof NetworkError ? e.message : "Não foi possível entrar.");
      setLoading(false);
    }
  }

  return (
    <div className="login">
      <section className="login__hero" aria-label="Sobre o Motor">
        <span className="login__disc" aria-hidden="true" />
        <span className="brand login__brand"><i aria-hidden="true" />Motor<small>banco</small></span>
        <div>
          <p className="login__headline">Pix na hora, <em>uma vez só.</em></p>
          <ul className="login__points">
            <li><Icon name="lock" size={18} />Transação ACID: débito, crédito e comprovante gravados juntos, ou nada.</li>
            <li><Icon name="shield" size={18} />Idempotência: se a rede cair e você reenviar, o dinheiro sai uma vez.</li>
            <li><Icon name="key" size={18} />Login com JWT e cada operação presa à conta do token.</li>
          </ul>
        </div>
        <p className="login__foot mono">Projeto de portfólio · Java 21 · Spring Boot · dados fictícios</p>
      </section>

      <section className="login__panel">
        <form className="login__form login__card" onSubmit={submit} noValidate>
          <div>
            <h1>Acesse sua conta</h1>
            <p className="muted">Use uma das contas de demonstração abaixo.</p>
          </div>

          {error && <div className="alert alert--error" role="alert"><Icon name="alert" size={18} />{error}</div>}

          <div className="field">
            <label htmlFor="email">E-mail</label>
            <input id="email" className="input" type="email" autoComplete="username" required
              value={email} onChange={(e) => setEmail(e.target.value)} placeholder="voce@motor.dev" />
          </div>
          <div className="field">
            <label htmlFor="password">Senha</label>
            <div className="input-group">
              <input id="password" className="input" type={showPassword ? "text" : "password"} autoComplete="current-password" required
                value={password} onChange={(e) => setPassword(e.target.value)} />
              <button type="button" className="icon-btn" onClick={() => setShowPassword((s) => !s)}
                aria-label={showPassword ? "Esconder senha" : "Mostrar senha"}>
                <Icon name={showPassword ? "eyeOff" : "eye"} size={18} />
              </button>
            </div>
          </div>

          <button className="btn btn--primary btn--lg btn--block" type="submit" disabled={loading || !email || !password}>
            {loading ? "Entrando…" : "Entrar"}
          </button>

          <div className="login__demo">
            <span className="eyebrow">Contas de demonstração · senha {DEMO_PASSWORD}</span>
            <div className="demo-users">
              {DEMO_USERS.map((user) => (
                <button key={user.email} type="button" className="demo-user" aria-pressed={email === user.email}
                  onClick={() => { setEmail(user.email); setPassword(DEMO_PASSWORD); setError(null); }}>
                  <span className="avatar avatar--soft" aria-hidden="true">{user.name.split(" ").map((p) => p[0]).slice(0, 2).join("")}</span>
                  <span className="demo-user__text"><b>{user.name}</b><span>{user.note}</span></span>
                </button>
              ))}
            </div>
          </div>
        </form>
      </section>
    </div>
  );
}

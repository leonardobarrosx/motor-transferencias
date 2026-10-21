import { useEffect, useState } from "react";
import { api, type Account } from "./api";
import { Nav } from "./components/Nav";
import { useRoute, useScheme, useStoredState } from "./hooks/preferences";
import { Dashboard } from "./screens/Dashboard";
import { Transfer } from "./screens/Transfer";

const DEFAULT_ACCOUNT = "0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1001";

export function App() {
  const route = useRoute();
  const [scheme, setScheme] = useScheme();
  const [accountId, setAccountId] = useStoredState("mt:account", DEFAULT_ACCOUNT);
  const [accounts, setAccounts] = useState<Account[]>([]);

  useEffect(() => {
    api.listAccounts()
      .then((list) => {
        setAccounts(list);
        // conta salva que não existe mais (banco recriado): volta para a primeira
        if (list.length > 0 && !list.some((a) => a.id === accountId)) setAccountId(list[0]!.id);
      })
      .catch(() => setAccounts([]));
  }, []);

  useEffect(() => {
    document.title = route === "transferir" ? "Transferir · Motor" : "Conta · Motor de Transferências";
  }, [route]);

  return (
    <>
      <a href="#conteudo" className="skip">Pular para o conteúdo</a>
      <div className="grain" aria-hidden="true" />
      <Nav
        route={route}
        accounts={accounts}
        accountId={accountId}
        onAccountChange={setAccountId}
        scheme={scheme}
        onSchemeChange={setScheme}
      />
      <main id="conteudo" className="main wrap" tabIndex={-1}>
        {api.mode === "demo" && (
          <p className="banner mono">
            <i aria-hidden="true" />Modo demonstração: motor em memória no navegador. Defina VITE_API_URL para usar a API.
          </p>
        )}
        {route === "transferir"
          ? <Transfer accountId={accountId} accounts={accounts} />
          : <Dashboard accountId={accountId} />}
        <footer className="footer mono">
          <span>Motor de Transferências · Leonardo Barros</span>
          <span>Java 21 · Spring Boot · PostgreSQL · React</span>
        </footer>
      </main>
    </>
  );
}

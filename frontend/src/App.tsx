import { useCallback, useEffect, useState } from "react";
import { api, type Account } from "./api";
import { Shell } from "./components/Shell";
import { AccountContext, UiProvider, useRoute, useScheme, useSession } from "./hooks/app";
import { Home } from "./screens/Home";
import { Login } from "./screens/Login";
import { Pix } from "./screens/Pix";
import { Statement } from "./screens/Statement";

const TITLES = { inicio: "Início", pix: "Pix", extrato: "Extrato" } as const;

export function App() {
  useScheme();
  const current = useSession();
  return <UiProvider>{current ? <Authenticated key={current.token} /> : <Login />}</UiProvider>;
}

function Authenticated() {
  const route = useRoute();
  const [account, setAccount] = useState<Account | null>(null);

  // relê saldo e resumo ao trocar de tela e depois de cada Pix
  const refresh = useCallback(() => { api.me().then(setAccount).catch(() => {}); }, []);
  useEffect(refresh, [refresh, route.name]);

  useEffect(() => { document.title = `${TITLES[route.name]} · Motor`; }, [route.name]);

  return (
    <AccountContext.Provider value={{ account, refresh }}>
      <Shell account={account}>
        {route.name === "pix" && <Pix key={route.params.get("chave") ?? "pix"} account={account} onCompleted={refresh} />}
        {route.name === "extrato" && <Statement />}
        {route.name === "inicio" && <Home account={account} />}
      </Shell>
    </AccountContext.Provider>
  );
}

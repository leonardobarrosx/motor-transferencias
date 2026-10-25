# ADR 0003 · Autenticação com JWT e conta sempre vinda do token

**Status:** aceito

## Contexto

A primeira versão recebia `sourceAccountId` no corpo do `POST /transfers`. Qualquer cliente podia debitar qualquer
conta só trocando esse id: uma falha de **IDOR** (*Insecure Direct Object Reference*), a número 1 do OWASP API Top 10
(*Broken Object Level Authorization*). O mesmo valia para saldo, extrato e comprovante.

## Decisão

- **Login** em `POST /api/v1/auth/login` (e-mail + senha). A senha só existe como hash **BCrypt** (custo 10).
- O login devolve um **access token JWT** assinado com **HS256**, válido por 30 minutos, com `sub` (usuário),
  `account_id` e `iss`. É emitido pelo `JwtEncoder` do Spring Security (Nimbus), sem biblioteca extra.
- A API é um **resource server** stateless: o filtro do Spring Security valida assinatura, expiração e emissor de
  cada requisição. Sem sessão no servidor, sem cookie, e por isso sem CSRF.
- **A conta nunca vem do cliente.** Os controllers recebem `@CurrentAccount UUID accountId`, resolvido por um
  `HandlerMethodArgumentResolver` a partir do claim `account_id` do token já validado. As rotas de conta viraram
  `/api/v1/me/**`, e não existe rota que aceite o id de outra conta.
- **Comprovante de terceiro devolve 404, não 403.** Um 403 confirmaria que aquele id de transferência existe.
- **Login não revela quem tem conta.** E-mail inexistente e senha errada recebem a mesma resposta
  (`401 INVALID_CREDENTIALS`), e o BCrypt roda nos dois casos para o tempo de resposta não denunciar a diferença.
- **Segredo fraco derruba o boot.** `JwtProperties` recusa chave com menos de 32 bytes.

## Alternativas consideradas

| Opção | Por que não agora |
|---|---|
| Sessão com cookie (`JSESSIONID`) | exige estado no servidor e proteção CSRF; menos natural para um front separado na Vercel |
| OAuth2 completo com Authorization Server (Keycloak, Spring Authorization Server) | o certo em produção bancária, mas é infraestrutura a mais para um simulador; a API já é resource server e trocar o emissor é configuração |
| RS256 (chave assimétrica) | melhor quando outros serviços validam o token sem poder emitir; com um único serviço, HS256 basta |
| Refresh token | aumenta a superfície; o token curto de 30 min com novo login atende a demonstração |

## Consequências

`ApiSecurityIT` prova pelo HTTP, com Postgres real: rota protegida sem token dá 401; um token com o `account_id`
trocado no payload dá 401 (assinatura não confere); o recebedor vê o comprovante e um terceiro recebe 404; e-mail
inexistente e senha errada são indistinguíveis. `TransferControllerTest.sourceAccountAlwaysComesFromTheToken` garante
que um `sourceAccountId` forjado no corpo é ignorado.

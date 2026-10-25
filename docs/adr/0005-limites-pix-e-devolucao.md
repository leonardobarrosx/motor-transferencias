# ADR 0005 · Limites Pix por período e devolução como nova transferência

**Status:** aceito

## Contexto

Dois requisitos que todo banco brasileiro tem, e que um core de Pix sem eles não convence:

1. **Limite por período.** Desde 2021 o Banco Central exige um limite menor à noite (20h às 6h) para reduzir golpes e
   sequestros-relâmpago. O cliente vê quanto ainda pode enviar no período.
2. **Devolução.** Quem recebeu um Pix por engano, ou num golpe, devolve, total ou parcialmente. No Pix real isso vira
   uma mensagem ISO 20022 pacs.004, com identificador próprio começando em `D` e o motivo codificado.

## Decisão

### Limites

- Duas janelas no horário de Brasília: **diurna (06h–20h, R$ 5.000)** e **noturna (20h–06h, R$ 1.000)**, guardadas
  por conta (`daytime_pix_limit`, `nighttime_pix_limit`).
- A janela noturna atravessa a meia-noite: às 02h, ela começou às 20h **de ontem**. A regra vive numa função pura
  (`PixLimits.windowAt`), testada nas bordas (05:59:59, 06:00, 19:59:59, 20:00, 02:00).
- O "já usado" é a soma dos Pix concluídos desde o início da janela, **calculada com a conta pagadora travada**
  (`SELECT … FOR UPDATE`). Sem o lock, dez envios simultâneos leriam o mesmo "usado" e todos passariam.
- Devoluções não consomem limite de quem devolve.
- Ordem das checagens: conta bloqueada → saldo → limite. A recusa por limite (`LIMIT_EXCEEDED`) é gravada e
  idempotente como as outras.
- `GET /api/v1/me/limits` mostra período, limite, usado, disponível e quando a janela vira.

### Devolução

- `POST /api/v1/transfers/{id}/refunds` com `Idempotency-Key`, valor e motivo (`CUSTOMER_REQUEST` → MD06,
  `FRAUD` → FR01, `BANK_ERROR` → BE08).
- **É uma transferência nova** (`kind = REFUND`, `original_transfer_id`), no sentido contrário, com o próprio par de
  lançamentos. O livro-razão nunca é editado: um banco audita o que aconteceu, não o estado final.
- Regras: só quem **recebeu** devolve; o Pix original precisa estar concluído; prazo de 90 dias; a soma das devoluções
  não passa do valor original.
- A soma já devolvida é conferida **com as duas contas travadas**, na mesma ordem de lock do Pix: duas devoluções
  simultâneas do mesmo Pix não conseguem passar do original juntas.
- Quem não participou do Pix original recebe 404, como no comprovante.
- O evento leva `kind`, `originalEndToEndId` e o código do motivo; o `motor-integracoes` gera **pacs.004**
  (PaymentReturn) em vez de pacs.008, e notificações próprias ("Devolução recebida").

## Alternativas consideradas

| Opção | Por que não |
|---|---|
| Contador de "usado no período" numa coluna da conta | precisa ser zerado na virada da janela (job, ou lógica de "resetar se mudou o período"); a soma por índice `(source_account_id, created_at)` é exata e não tem estado para dessincronizar |
| Estorno com `UPDATE` no status da transferência original | apaga a história; auditoria e conciliação precisam ver o débito original e o crédito de volta como fatos separados |
| Lock na linha da transferência original para a devolução | funcionaria, mas as duas contas já são travadas pela mesma regra do Pix; uma trava a menos para entender |

## Consequências

`LimitsAndRefundsIT` roda contra Postgres real com o relógio parado às 20h30: dez Pix simultâneos de R$ 150 com limite
noturno de R$ 1.000 resultam em exatamente 6 concluídos e 4 `LIMIT_EXCEEDED`; oito devoluções simultâneas de R$ 30
sobre um Pix de R$ 100 resultam em exatamente 3 concluídas. `PixLimitsTest` cobre as bordas das janelas, e
`RotasPixTest` prova a pacs.004 com o E2E original e o código MD06.

Fora do escopo: alteração de limite pelo cliente (no mundo real, aumento tem carência de 24 a 48 horas e redução é
imediata), MED completo (bloqueio cautelar e disputa entre bancos) e limite por transação.

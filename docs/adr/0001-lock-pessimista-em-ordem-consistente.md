# ADR 0001 · Lock pessimista em ordem consistente

**Status:** aceito

## Contexto

Duas transferências que tocam a mesma conta ao mesmo tempo leem o mesmo saldo. Sem controle, as duas debitam
sobre o valor antigo e uma escrita apaga a outra (*lost update*): dinheiro aparece ou some.

As opções avaliadas:

| Opção | Prós | Contras |
|---|---|---|
| Isolamento `SERIALIZABLE` | correto sem pensar em locks | aborta transações sob disputa; o cliente precisa repetir; difícil de explicar quando falha |
| Lock otimista (`@Version`) | sem espera, bom com pouca disputa | sob disputa (uma conta muito movimentada) quase toda tentativa falha e é repetida |
| `UPDATE ... SET balance = balance - x WHERE balance >= x` | uma instrução, atômica | a regra de negócio vai para o SQL; o livro-razão precisa do saldo resultante, que exige outra leitura |
| **`SELECT ... FOR UPDATE` nas duas contas** | serializa só quem disputa a mesma conta; regra fica no domínio | precisa de ordem de lock para não haver deadlock |

## Decisão

`READ COMMITTED` (padrão do Postgres) com `SELECT ... FOR UPDATE` nas duas contas, sempre **na mesma ordem**:
o menor UUID primeiro, comparado sem sinal, exatamente como o Postgres ordena `uuid`.

Detalhes que importam:

- **Ordem de lock.** A→B e B→A simultâneas travariam uma conta cada e esperariam a outra. Com a ordem fixa, as duas
  pedem primeiro a mesma conta e uma espera a outra terminar. `UUID.compareTo` do Java compara com sinal
  (`ffffffff-…` vem antes de `00000000-…`), por isso o motor usa uma comparação própria, sem sinal. O teste
  `ordersLikePostgresNotLikeUuidCompareTo` trava essa decisão.
- **Nada carregado antes do lock.** O destino é resolvido pela chave Pix com uma consulta que devolve só o id. Se a
  entidade fosse carregada antes, ficaria no contexto de persistência com um saldo lido sem trava, e o
  `SELECT ... FOR UPDATE` seguinte devolveria a mesma instância desatualizada.
- **`@Version` como segunda trava.** Se um caminho futuro esquecer o lock pessimista, a escrita concorrente falha em
  vez de sobrescrever.
- **`lock_timeout = 5s`.** Uma transferência não segura conexão do pool esperando indefinidamente; estoura como
  `409 ACCOUNT_BUSY` com `Retry-After`, e repetir com a mesma `Idempotency-Key` é seguro.
- **`CHECK (balance >= 0)`** no banco: se tudo acima falhar, o Postgres recusa o saldo negativo.

## Consequências

Transferências entre contas diferentes correm em paralelo; só as que disputam a mesma conta fazem fila. O teste
`TransferConcurrencyIT` dispara 60 transferências cruzadas A↔B ao mesmo tempo contra Postgres real e exige zero
deadlock e dinheiro conservado, e 50 débitos simultâneos numa conta com saldo para 30, exigindo exatamente 30
concluídos e saldo final zero.

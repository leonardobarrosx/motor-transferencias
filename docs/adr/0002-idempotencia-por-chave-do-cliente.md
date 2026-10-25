# ADR 0002 · Idempotência por chave do cliente

**Status:** aceito

## Contexto

Em pagamento, o pior erro de rede é o silencioso: o servidor debitou, a resposta se perdeu, o aplicativo tenta de
novo. Sem proteção, a pessoa paga duas vezes. O cliente não tem como saber se o primeiro envio chegou.

## Decisão

Todo `POST /api/v1/transfers` exige o header `Idempotency-Key` (UUID), gerado pelo cliente **por intenção de
transferir**, não por requisição HTTP. A chave é gravada na própria linha da transferência, com
`UNIQUE (source_account_id, idempotency_key)`, junto com o SHA-256 do pedido normalizado. A chave é única **por conta de origem**: a chave de um cliente nunca colide com a de outro, nem revela que já foi usada (até a V5 ela era global; a mudança e o teste que a prova estão em `V5__idempotency_key_per_account.sql` e `TransferConcurrencyIT`).

| Situação | Resposta |
|---|---|
| Chave nova | processa: `201 Created` (concluída) ou `422 TRANSFER_REJECTED` (recusada) |
| Chave conhecida, mesmo pedido | devolve o resultado gravado, sem reprocessar: `200` ou `422`, com `Idempotent-Replayed: true` |
| Chave conhecida, pedido diferente | `422 IDEMPOTENCY_KEY_REUSED` |
| Duas requisições com a mesma chave ao mesmo tempo | uma grava; a outra bate na `UNIQUE`, sofre rollback inteiro e devolve o que a primeira gravou |

Decisões de detalhe:

- **A chave mora na transferência, não numa tabela à parte.** Gravar a ordem e a chave na mesma linha, na mesma
  transação que move o saldo, elimina o estado "chave reservada, transferência não feita".
- **Recusas também são gravadas.** Uma transferência recusada por saldo insuficiente, repetida depois que a conta
  recebeu dinheiro, continua recusada. A decisão foi tomada uma vez; quem quiser tentar de novo faz um pedido novo,
  com chave nova. Erros de validação (400) e chave Pix inexistente (404) não são gravados: nada foi decidido.
- **Hash do pedido normalizado.** `"100"` e `"100.00"`, ou espaços em volta da chave Pix, são o mesmo pedido. Outro
  valor, outra chave Pix ou outra descrição não são.
- **A corrida se resolve fora da transação.** `TransferService` não é `@Transactional`: a perdedora só consegue ler o
  que a vencedora gravou depois que a própria transação foi desfeita. `TransferProcessor` é a transação.
- **`saveAndFlush` antes de mexer nos saldos.** A violação de unicidade aparece cedo, antes de qualquer débito ser
  enviado ao banco.

## Consequências

O cliente pode repetir sem medo depois de timeout, queda de rede ou `409 ACCOUNT_BUSY`. O frontend demonstra isso:
com **Simular queda de rede** ligado, o primeiro envio chega ao motor mas a resposta é descartada; o reenvio usa a
mesma chave e recebe o comprovante original com `Idempotent-Replayed: true`. O teste
`sameIdempotencyKeyFiredConcurrentlyMovesMoneyOnce` dispara 20 requisições com a mesma chave ao mesmo tempo e exige
uma única transferência e um único débito.

Fora do escopo por ora: expiração das chaves (em produção, um job removeria chaves com mais de N dias) e escopo da
chave por cliente autenticado.

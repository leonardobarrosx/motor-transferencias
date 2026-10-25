# Contrato de eventos

O `motor-transferencias` publica um evento por transferência decidida. Quem consome não acessa o banco do motor:
tudo o que precisa vem no evento.

## Tópicos

| Tópico | Partições | Produtor | Consumidor | Chave | Conteúdo |
|---|---|---|---|---|---|
| `pix.transfers` | 3 | motor (via outbox) | motor-integracoes | id da conta pagadora | `TransferSubmitted`, `TransferCompleted`, `TransferRejected` |
| `pix.spi.results` | 3 | motor-integracoes (SPI simulado) | motor | id da conta pagadora | resposta do SPI a um Pix para outro banco |
| `pix.notifications` | 3 | motor-integracoes | (canais de envio: push, e-mail) | id da conta notificada | notificação pronta |
| `pix.transfers.dlq` | 1 | motor-integracoes | operação / reprocessamento | igual à original | mensagem original que falhou em todas as tentativas |

**Por que a chave é a conta pagadora:** o Kafka só garante ordem dentro de uma partição, e mensagens com a mesma
chave vão sempre para a mesma partição. Assim, os eventos de uma conta chegam na ordem em que aconteceram.

**Headers:** `eventType` e `eventId`, para filtrar e rastrear sem desserializar o corpo, e `traceparent` (W3C Trace
Context) com o trace da requisição HTTP que criou o Pix. O `motor-integracoes` repassa o `traceparent` nas
notificações que publica, então um mesmo `traceId` liga o app, a API, o Kafka e o Camel.

## Tipos de evento

| `eventType` | Quando | O que o `motor-integracoes` faz |
|---|---|---|
| `TransferSubmitted` | Pix para **outra instituição** aceito pelo motor (status `PENDING`, valor reservado) | gera a pacs.008 para o SPI e devolve a resposta em `pix.spi.results` |
| `TransferCompleted` | Pix concluído: entre contas daqui na hora; para outro banco, quando o SPI liquida | notifica e concilia. **Não** vai ao SPI: Pix entre clientes do mesmo banco é liquidado internamente |
| `TransferRejected` | recusado aqui (saldo, limite, bloqueio) ou pelo banco recebedor (`RECEIVER_REJECTED`) | notifica o pagador |

Em Pix para outro banco, o `payee` tem `accountId: null` e o `ispb` da instituição de destino.

## `pix.spi.results`

```json
{
  "eventId": "6f1c…",
  "transferId": "0e58…",
  "endToEndId": "E9999999920261025…",
  "status": "ACSC",
  "reasonCode": null,
  "decidedAt": "2026-10-25T14:40:21Z"
}
```

`status` é `ACSC` (liquidado) ou `RJCT` (recusado, com `reasonCode` ISO 20022, ex.: `AC03` conta inválida). O motor
aplica a resposta com a conta pagadora travada e ignora respostas repetidas: a transferência só sai de `PENDING` uma vez.

## `TransferSubmitted` / `TransferCompleted` / `TransferRejected` (schemaVersion 1)

```json
{
  "eventId": "a5e900e9-f688-4fff-9480-068df1cc2b4e",
  "eventType": "TransferCompleted",
  "schemaVersion": 1,
  "occurredAt": "2026-10-25T11:26:49.731Z",
  "transferId": "82bfe92c-f3be-49ad-bc7c-cf92c2848f00",
  "endToEndId": "E99999999202610251126K3ES17GE5RF",
  "amount": "150.75",
  "currency": "BRL",
  "description": "Aluguel de outubro",
  "rejectionReason": null,
  "kind": "PIX",
  "originalTransferId": null,
  "originalEndToEndId": null,
  "refundReasonCode": null,
  "payer": {
    "accountId": "0b0d8a52-6a0c-4c1e-9a3e-1f4f2a7d1001",
    "holderName": "Leonardo Barros",
    "taxId": "11144477700",
    "ispb": "99999999",
    "branch": "0001",
    "accountNumber": "10001-7",
    "pixKey": "leonardo@motor.dev"
  },
  "payee": { "...": "mesmos campos" }
}
```

| Campo | Observação |
|---|---|
| `eventId` | único por evento; o consumidor deduplica por ele (entrega pelo menos uma vez) |
| `amount` | string decimal, nunca número: o consumidor não pode perder centavos com float |
| `rejectionReason` | só em `TransferRejected`: `INSUFFICIENT_FUNDS`, `SOURCE_ACCOUNT_BLOCKED`, `TARGET_ACCOUNT_BLOCKED`, `LIMIT_EXCEEDED` |
| `kind` | `PIX` ou `REFUND` (devolução). Eventos antigos, sem o campo, são `PIX` |
| `originalTransferId`, `originalEndToEndId` | só em devoluções: o Pix que está sendo devolvido. O `endToEndId` da devolução começa com `D` |
| `refundReasonCode` | só em devoluções: código ISO 20022 do motivo (`MD06` pedido do cliente, `FR01` fraude, `BE08` erro do banco) |
| `taxId` | CPF/CNPJ completo, porque a mensagem pacs.008 do SPI exige. É dado pessoal: em produção, o tópico teria ACL restrita e criptografia em trânsito (TLS) e em repouso |

Os campos `kind`, `originalTransferId`, `originalEndToEndId` e `refundReasonCode` foram acrescentados depois da
primeira versão, sem trocar o `schemaVersion`: é o caso de evolução compatível descrito abaixo.

## Evolução do contrato

- **Acrescentar campo:** permitido. O consumidor ignora campos desconhecidos (`@JsonIgnoreProperties(ignoreUnknown = true)`),
  e um teste de rota manda um evento com um campo novo para provar isso.
- **Remover ou mudar o tipo de um campo:** exige `schemaVersion` novo, e o consumidor passa a tratar as duas versões
  durante a transição.
- **Próximo passo natural:** Avro ou Protobuf com Schema Registry, para a compatibilidade ser verificada na hora de
  publicar, e não descoberta em produção.
